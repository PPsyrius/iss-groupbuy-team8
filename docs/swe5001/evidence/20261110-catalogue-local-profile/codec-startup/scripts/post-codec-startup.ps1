$ErrorActionPreference = 'Stop'
$Root = 'C:\Users\sirip\AppData\Local\Temp\opencode\swe5001-profile-20261110'
$Matrix = Join-Path $Root 'matrix-v2'
$Evidence = 'C:\Users\sirip\Documents\NUS\Sem1\GroupProject\iss-groupbuy-team8\docs\swe5001\evidence\20261110-catalogue-local-profile\codec-startup'
$Jdk = 'eclipse-temurin:21-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d'

# The owning session must run this only after its one docker-wait completion.
# This script never launches an application, load generator or build.
if (Test-Path $Evidence) { throw 'Evidence directory already exists; refusing overwrite' }
if (Test-Path (Join-Path $Matrix 'codec-startup-postrun-exits.json')) { throw 'Post-run receipt already exists; refusing overwrite' }
$PythonArgs = @('run', '--rm', '--pull=never', '--network', 'none', '--cpus=0.5', '--memory=256m', '--read-only', '--tmpfs', '/tmp:rw,size=64m', '--mount', "type=bind,source=$Root,target=/results", 'python:3.13.8-slim')
& docker @PythonArgs python /results/matrix-v2/retain-codec-startup.py prepare
if ($LASTEXITCODE -ne 0) { throw 'Preparation refused; no analysis or evidence overwritten' }

$Exits = [ordered]@{ kind = 'codec-startup-postrun'; analyzer = $null; jfr = @() }
& docker @PythonArgs python /results/matrix-v2/analyze-codec-startup.py
$Exits.analyzer = $LASTEXITCODE
if ($LASTEXITCODE -ne 0) { Write-Warning 'Independent analyzer failed; partial sources will be retained without claiming success' }

$JavaArgs = @('run', '--rm', '--pull=never', '--network', 'none', '--cpus=0.5', '--memory=512m', '--read-only', '--tmpfs', '/tmp:rw,size=128m', '--mount', "type=bind,source=$Root,target=/results", $Jdk, 'java', '-Xmx256m')
foreach ($Prefix in (Get-Content (Join-Path $Matrix 'codec-startup-jfr-prefixes.txt'))) {
    foreach ($Helper in @('JfrFailureSummary.java', 'JfrCodecSummary.java')) {
        $Suffix = if ($Helper -eq 'JfrFailureSummary.java') { '-jfr-analysis.json' } else { '-cold-jfr-analysis.json' }
        if (Test-Path (Join-Path $Matrix "$Prefix$Suffix")) { throw 'JFR analysis already exists; refusing overwrite' }
        & docker @JavaArgs "/results/matrix-v2/$Helper" $Prefix
        $Exits.jfr += [ordered]@{ prefix = $Prefix; helper = $Helper; exitCode = $LASTEXITCODE }
        if ($LASTEXITCODE -ne 0) { Write-Warning "JFR helper failed for $Prefix; missing output is not zero contention" }
    }
}
$Exits | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $Matrix 'codec-startup-postrun-exits.json')
New-Item -ItemType Directory -Path $Evidence | Out-Null
$RetainArgs = @('run', '--rm', '--pull=never', '--network', 'none', '--cpus=0.5', '--memory=256m', '--read-only', '--tmpfs', '/tmp:rw,size=64m', '--mount', "type=bind,source=$Root,target=/results,readonly", '--mount', "type=bind,source=$Evidence,target=/evidence", 'python:3.13.8-slim')
& docker @RetainArgs python /results/matrix-v2/retain-codec-startup.py retain
if ($LASTEXITCODE -ne 0) { throw 'Retention failed; preserve all partial evidence and do not rerun blindly' }
