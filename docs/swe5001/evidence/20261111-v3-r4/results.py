"""Check the portable R4 package, or regenerate its CSVs with --write.

Python standard library only. No AWS/network access or raw private inputs.
CSV generation is a projection, not a rerun of k6 or the original V3 analyzer.
"""
import argparse
import csv
from datetime import datetime, timedelta
import hashlib
import io
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def read_json(name):
    return json.loads((HERE / name).read_text(encoding='utf-8'))


def read_lines(name):
    return [json.loads(line) for line in (HERE / name).read_text(encoding='utf-8').splitlines()]


def at(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def csv_bytes(rows):
    stream = io.StringIO(newline='')
    writer = csv.DictWriter(stream, fieldnames=list(rows[0]), lineterminator='\n')
    writer.writeheader()
    writer.writerows(rows)
    return stream.getvalue().encode('utf-8')


def project():
    evidence = read_json('verification.json')
    raw = evidence['rawRecount']
    minutes = raw['completionMinuteBins']
    assert len(minutes) == 30
    assert sum(row['browseSamples'] for row in minutes) == 725999
    assert sum(row['httpRequests'] for row in minutes) == 726000
    assert sum(row['browseErrors'] for row in minutes) == 0
    assert evidence['originalTools']['canonicalAnalyzer']['exitCode'] == 1
    browse = [{**row, 'browseCompletionsPerSecond': row['browseSamples'] / 60} for row in minutes]

    events = evidence['scaling']['activities']
    normalized = evidence['originalTools']['normalizer']['events']
    assert [event['desiredTarget'] for event in events] == [2, 3, 4, 6]
    activities = []
    for event, transition in zip(events, normalized, strict=True):
        assert at(event['startTime']) == at(transition['at'])
        assert event['desiredTarget'] == transition['to'] and event['status'] == 'Successful'
        activities.append({
            'fromDesired': transition['from'], 'toDesired': transition['to'],
            **{key: event[key] for key in ('startTime', 'endTime', 'status', 'causeClass',
                 'desiredFirstObservedAtOrAboveTarget', 'runningFirstObservedAtOrAboveTarget')},
        })

    observations = read_lines('service-observations.jsonl')
    coverage = evidence['scaling']['observationCoverage']
    assert len(observations) == coverage['derivedUnion']['samples'] == 264
    assert [row['observedAt'] for row in observations] == sorted(row['observedAt'] for row in observations)
    times = [at(row['observedAt']) for row in observations]
    start = at(evidence['generatorResources']['coverage']['start'])
    end = at(evidence['generatorResources']['coverage']['end'])
    assert times[0] < start < end < times[-1]
    assert sum(start <= value <= end for value in times) == 200
    gaps = [(b - a).total_seconds() for a, b in zip(times, times[1:]) if b >= start and a <= end]
    assert abs(max(gaps) - coverage['derivedUnion']['maximumGapAcrossRunIncludingBracketsSeconds']) < 1e-6
    for row in observations:
        source = coverage[row['sourceKind']]['source']
        assert row['source']['sha256'] == source['sha256']
        assert row['source']['path'] == source['path']
        assert row['source']['line'] > 0 and len(row['sourceLineSha256']) == 64

    points = read_lines('cloudwatch-points.jsonl')
    metadata = read_json('cloudwatch-series.json')
    assert len(points) == 793
    indexed = {}
    for point in points:
        series = next(item for item in metadata[point['sourceKind']]['series'] if item['identity'] == point['identity'])
        assert point['source']['sha256'] == series['result']['sha256']
        assert point['query']['sha256'] == series['query']['sha256']
        assert point['unit'] == series['unit'] and point['periodSeconds'] == 60
        key = (at(point['minute']), point['identity'], point['sourceKind'])
        assert key not in indexed
        indexed[key] = point['value']

    columns = {
        'albRequestsSum': ('AWS/ApplicationELB/RequestCount/Sum', 1),
        'albRequestsPerTargetSum': ('AWS/ApplicationELB/RequestCountPerTarget/Sum', 1),
        'albTargetP95Ms': ('AWS/ApplicationELB/TargetResponseTime/p95', 1000),
        'albTargetP99Ms': ('AWS/ApplicationELB/TargetResponseTime/p99', 1000),
        'commerceCpuAveragePercent': ('AWS/ECS/CPUUtilization/Average/commerce-svc', 1),
        'commerceMemoryAveragePercent': ('AWS/ECS/MemoryUtilization/Average/commerce-svc', 1),
        'rdsCpuAveragePercent': ('AWS/RDS/CPUUtilization/Average', 1),
        'rdsConnectionsMaximum': ('AWS/RDS/DatabaseConnections/Maximum', 1),
        'target5xxSum': ('AWS/ApplicationELB/HTTPCode_Target_5XX_Count/Sum', 1),
    }
    server = []
    minute = start.replace(second=0, microsecond=0)
    while minute <= end:
        observed = [row for row in observations if at(row['observedAt']).replace(second=0, microsecond=0) == minute and start <= at(row['observedAt']) <= end]
        row = {'minuteUtc': minute.isoformat(), 'partialRunEdgeMinute': minute < start or minute + timedelta(minutes=1) > end}
        for field in ('desired', 'running', 'pending'):
            row[field + 'SampledValues'] = ';'.join(str(value) for value in sorted({item[field] for item in observed}))
        for column, (identity, factor) in columns.items():
            value = indexed.get((minute, identity, 'supplemental'), indexed.get((minute, identity, 'canonical')))
            row[column] = '' if value is None else value * factor
        server.append(row)
        minute += timedelta(minutes=1)
    assert len(server) == 31 and all(row['target5xxSum'] == '' for row in server)
    assert server[-1]['commerceCpuAveragePercent'] == ''
    assert server[-1]['rdsConnectionsMaximum'] == 40
    return {'browse-minutes.csv': csv_bytes(browse), 'scaling-activities.csv': csv_bytes(activities), 'server-minutes.csv': csv_bytes(server)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='Regenerate three derived CSV files only; SHA256SUMS must then be reviewed/refreshed separately')
    args = parser.parse_args()
    outputs = project()
    for name, content in outputs.items():
        if args.write:
            (HERE / name).write_bytes(content)
        else:
            assert (HERE / name).read_bytes() == content, f'CSV differs: {name}'
    if not args.write:
        listed = set()
        for line in (HERE / 'SHA256SUMS').read_text(encoding='ascii').splitlines():
            digest, name = line.split('  ', 1)
            assert Path(name).name == name and name not in listed
            assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == digest, f'Hash differs: {name}'
            listed.add(name)
        actual = {path.name for path in HERE.iterdir() if path.is_file() and path.name != 'SHA256SUMS'}
        assert listed == actual, 'Manifest does not cover exact package file set'
    print(json.dumps({'mode': 'write-csv' if args.write else 'check', 'csvFiles': len(outputs), 'browseMinuteBins': 30, 'scalingActions': 4, 'serviceObservations': 264, 'cloudwatchPoints': 793, 'serverMinutes': 31, 'canonicalAnalyzerExitPreserved': 1}))


if __name__ == '__main__':
    main()
