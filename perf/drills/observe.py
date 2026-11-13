#!/usr/bin/env python3
"""Bounded read-only ECS/queue capture for #459. Never injects or restores faults."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import runpy
import signal
import time

REPO = Path(__file__).resolve().parents[2]
SUPPORT = runpy.run_path(str(REPO / 'infra/swe5001/scripts/observe-load.py'))
LOAD = SUPPORT['LOAD']
READS = {('sts', 'get-caller-identity'), ('ecs', 'describe-services'), ('ecs', 'list-tasks'),
         ('ecs', 'describe-tasks'), ('sqs', 'get-queue-attributes'), ('cloudwatch', 'describe-alarms')}
SERVICES = ('commerce-svc', 'identity-svc', 'notification-svc')


def read_aws(*args):
    LOAD['require'](args[:2] in READS, 'Drill observer refused a mutation/unsupported operation')
    return LOAD['aws'](*args, '--cli-connect-timeout', '3', '--cli-read-timeout', '5')


def observation(phase, read=read_aws):
    row = read('ecs', 'describe-services', '--cluster', 'blocbuy-swe5001', '--services', *SERVICES,
        '--query', '{services:services[].{name:serviceName,status:status,desired:desiredCount,running:runningCount,pending:pendingCount,taskDefinition:taskDefinition,deployments:deployments[].{rolloutState:rolloutState},events:events[:10]},failures:failures}')
    LOAD['require'](row.get('failures') == [] and len(row.get('services', [])) == 3
        and {s['name'] for s in row['services']} == set(SERVICES), 'Incomplete service observation')
    arns = []
    for name in SERVICES:
        tasks = read('ecs', 'list-tasks', '--cluster', 'blocbuy-swe5001', '--service-name', name,
            '--desired-status', 'RUNNING', '--query', 'taskArns')
        LOAD['require'](isinstance(tasks, list) and all(isinstance(t, str) and t for t in tasks), 'Incomplete task listing')
        arns.extend(tasks)
    LOAD['require'](0 < len(arns) <= 30 and len(set(arns)) == len(arns), 'Unexpected task inventory')
    tasks = read('ecs', 'describe-tasks', '--cluster', 'blocbuy-swe5001', '--tasks', *arns, '--query',
        '{tasks:tasks[].{taskArn:taskArn,group:group,taskDefinition:taskDefinitionArn,lastStatus:lastStatus,desiredStatus:desiredStatus,createdAt:createdAt,startedAt:startedAt,availabilityZone:availabilityZone,containers:containers[].{name:name,imageDigest:imageDigest}},failures:failures}')
    LOAD['require'](tasks.get('failures') == [] and len(tasks.get('tasks', [])) == len(arns), 'Incomplete task details')
    row['tasks'] = tasks['tasks']
    if phase == 'v7':
        row['queues'] = {}
        for name in ('blocbuy-swe5001-notification', 'blocbuy-swe5001-notification-dlq'):
            result = read('sqs', 'get-queue-attributes', '--queue-url',
                f'https://sqs.ap-southeast-1.amazonaws.com/402178232802/{name}', '--attribute-names',
                'ApproximateNumberOfMessages', 'ApproximateNumberOfMessagesNotVisible', 'ApproximateNumberOfMessagesDelayed')
            attributes = result.get('Attributes', {})
            counts = {key: int(attributes[key]) for key in ('ApproximateNumberOfMessages',
                'ApproximateNumberOfMessagesNotVisible', 'ApproximateNumberOfMessagesDelayed')}
            LOAD['require'](all(value >= 0 for value in counts.values()), 'Invalid queue counts')
            row['queues'][name] = counts
        alarms = read('cloudwatch', 'describe-alarms', '--alarm-names', 'blocbuy-swe5001-notification-dlq-not-empty',
            '--query', 'MetricAlarms[].{name:AlarmName,state:StateValue,stateUpdatedAt:StateUpdatedTimestamp}')
        LOAD['require'](isinstance(alarms, list) and len(alarms) == 1, 'Missing DLQ alarm')
        row['alarms'] = alarms
    row['observedAt'] = LOAD['now']()
    return row


def capture(directory, phase, seconds):
    samples, previous, gap = 0, None, 0
    completed = False
    errors = []
    try:
        deadline = time.monotonic() + seconds
        with (directory / 'observations.jsonl').open('x', encoding='utf-8') as stream:
            os.chmod(stream.name, 0o600)
            while True:
                row = observation(phase)
                at = datetime.fromisoformat(row['observedAt'].replace('Z', '+00:00'))
                if previous is not None:
                    delta = (at - previous).total_seconds()
                    LOAD['require'](delta > 0, 'Clock did not advance')
                    gap = max(gap, delta)
                previous = at
                stream.write(json.dumps(row) + '\n')
                stream.flush()
                os.fsync(stream.fileno())
                samples += 1
                temporary = directory / 'heartbeat.tmp'
                temporary.write_text(json.dumps({'at': row['observedAt'], 'samples': samples, 'state': 'recording'}))
                os.chmod(temporary, 0o600)
                os.replace(temporary, directory / 'heartbeat.json')
                if samples == 1:
                    print('First real observation saved; keep this foreground process running. No fault was injected.', flush=True)
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    completed = True
                    break
                time.sleep(min(15, remaining))
    except (Exception, KeyboardInterrupt) as error:
        errors.append({'at': LOAD['now'](), 'errorType': type(error).__name__})
    finally:
        result = {'phase': phase, 'at': LOAD['now'](), 'samples': samples, 'maximumGapSeconds': gap,
            'collectionComplete': completed and samples >= 2 and gap <= 45 and not errors, 'errors': errors,
            'awsAcceptanceComplete': False, 'restorationPerformed': False}
        LOAD['write'](directory, 'observer-result.json', result)
        temporary = directory / 'heartbeat.tmp'
        temporary.write_text(json.dumps({'at': LOAD['now'](), 'samples': samples, 'state': 'finished'}))
        os.chmod(temporary, 0o600)
        os.replace(temporary, directory / 'heartbeat.json')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('v5', 'identity', 'v7'))
    parser.add_argument('directory', type=Path)
    parser.add_argument('--seconds', type=int, default=1200)
    parser.add_argument('--persistent-host', action='store_true')
    args = parser.parse_args()
    LOAD['require'](300 <= args.seconds <= 3600, 'Observation window must be 300..3600 seconds')
    SUPPORT['guard_host'](args.persistent_host)
    sha = LOAD['driver_sha']()
    LOAD['require'](read_aws('sts', 'get-caller-identity')['Account'] == '402178232802', 'Wrong AWS account')
    expiry = SUPPORT['credential_expiry'](args.seconds)
    directory = args.directory.resolve()
    LOAD['require'](not directory.exists() and directory != REPO and REPO not in directory.parents,
        'Use a new private results directory outside the checkout')
    directory.mkdir(parents=True, mode=0o700)
    LOAD['write'](directory, 'context.json', {'phase': args.phase, 'seconds': args.seconds,
        'toolingSha': sha, 'sourceFileSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'credentialExpiration': expiry, 'account': '402178232802', 'region': 'ap-southeast-1',
        'cluster': 'blocbuy-swe5001', 'persistentHostConfirmed': True})
    os.environ['AWS_MAX_ATTEMPTS'] = '1'
    def stop(_signal, _frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, stop)
    result = capture(directory, args.phase, args.seconds)
    print(json.dumps(result))
    return 0 if result['collectionComplete'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
