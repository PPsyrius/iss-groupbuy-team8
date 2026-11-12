$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$out = Join-Path $root 'prep/aws-generator-20261111/shareability-review'
$mounts = @('docs/swe5001/evidence/20261111-v3-r3','perf/k6/README.md','perf/k6/lib.mjs','perf/k6/lib.test.mjs','perf/k6/scaling.mjs','perf/k6/scaling.test.mjs','docs/agile/sprint-04/log/2026-11-11.md','docs/quality/nfr-register.md')
$argsList = @('run','--rm','--pull=never','--network=none','--cpus=1','--memory=512m','--read-only','--cap-drop=ALL','--security-opt=no-new-privileges')
foreach ($path in $mounts) { $argsList += @('--mount',"type=bind,source=$(Join-Path $root $path),target=/scan/$path,readonly") }
$argsList += @('ghcr.io/gitleaks/gitleaks:v8.30.1','dir','/scan','--redact=100','--report-format=json','--report-path=/dev/stdout','--no-banner','--log-level=error','--ignore-gitleaks-allow')
$raw = & docker @argsList 2>$null
$code = $LASTEXITCODE
$parsed = @()
$valid = $false
try { $parsed = @((($raw -join "`n") | ConvertFrom-Json)); $valid = $true } catch { }
$snapshot = Get-Content -LiteralPath (Join-Path $out 'snapshot-and-categories.json') -Raw | ConvertFrom-Json
$summary = [ordered]@{image='ghcr.io/gitleaks/gitleaks:v8.30.1'; exitCode=$code; reportParsed=$valid; snapshotFileCount=$snapshot.count; sharelistSha256=$snapshot.sharelistSha256; coverage='Prospective working-tree files via exact read-only mounts; default built-in rules; ignore-gitleaks-allow enabled; no repository config/history read'; findings=@($parsed | Where-Object { $_ } | Group-Object File,RuleID | ForEach-Object { [ordered]@{path=$_.Group[0].File.Replace('/scan/',''); rule=$_.Group[0].RuleID; count=$_.Count} }); limitation='Only path, rule and count retained; stdout report handled in memory, stderr suppressed; exit 0 + parsed empty findings required for clean scan. Files may change concurrently; separate hash stability receipt required.'}
$raw = $null
$parsed = $null
$json = $summary | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText((Join-Path $out 'gitleaks-summary.json'),$json,[Text.UTF8Encoding]::new($false))
$json
