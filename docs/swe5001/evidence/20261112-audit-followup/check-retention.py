"""Read-only checks. This program has no removal, archival or approval mode."""
from pathlib import Path
import hashlib
import json

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    plan = json.loads((HERE / 'retention-plan.json').read_text(encoding='utf-8'))
    assert not plan['deletionAuthorized'] and not plan['archiveCreated']
    assert not plan['retrievalVerified'] and plan['durableDestination'] is None
    candidate_rows = plan['conditionalExternalCandidates']
    candidates = {r['path'] for r in candidate_rows}
    protected = set(plan['keep'] + plan['preserveOwnerReview'] + plan['existingProtectedDependencies'])
    assert len(candidates) == len(candidate_rows) == 485
    assert not candidates.intersection(protected)
    for row in candidate_rows:
        path = ROOT / row['path']
        assert path.is_file() and path.stat().st_size == row['bytes'], row['path']
        assert digest(path) == row['sha256'], row['path']
    for name in protected:
        assert (ROOT / name).is_file(), name
    affected = plan['affectedManifests']
    assert len(affected) == 25
    obligated = {p for row in affected for p in row['conditionalExternalMembers']}
    assert len(obligated) == 456 and obligated <= candidates
    for row in affected:
        assert digest(ROOT / row['path']) == row['sha256'], row['path']
    privacy = json.loads((HERE / 'privacy-dispositions.json').read_text(encoding='utf-8'))
    for row in privacy['locations']:
        assert digest(ROOT / row['path']) == row['originalSha256'], row['path']
    print(json.dumps({
        'mode': 'read-only preservation check, NOT archive/removal readiness',
        'candidatesPresentAndUnchanged': len(candidates),
        'protectedPathsPresent': len(protected),
        'affectedHistoricalManifestsUnchanged': len(affected),
        'hashBoundCandidates': len(obligated),
        'privacyOriginalsUnchanged': len(privacy['locations']),
        'repositoryFilesRemoved': 0,
        'durableArchiveVerified': False,
        'metadataExposureResolved': False,
    }))

if __name__ == '__main__':
    main()
