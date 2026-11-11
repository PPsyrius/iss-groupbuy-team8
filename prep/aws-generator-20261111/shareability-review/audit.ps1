$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$out = Join-Path $root 'prep/aws-generator-20261111/shareability-review'
$paths = @('perf/k6/README.md','perf/k6/lib.mjs','perf/k6/lib.test.mjs','perf/k6/scaling.mjs','perf/k6/scaling.test.mjs','docs/agile/sprint-04/log/2026-11-11.md','docs/quality/nfr-register.md')
$evidence = 'docs/swe5001/evidence/20261111-v3-r3'
$paths += @(Get-ChildItem -LiteralPath (Join-Path $root $evidence) -File -Recurse | ForEach-Object { [IO.Path]::GetRelativePath($root, $_.FullName).Replace('\','/') })
$paths = @($paths | Sort-Object -CaseSensitive -Unique)
$patterns = [ordered]@{
  'private-ip' = '(?<![\d.])(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(?![\d.])'
  'operator-filesystem' = '(?i)(?:[A-Z]:[\\/](?:Users|home)[\\/][^\s"<>]+|/Users/[^\s"<>]+|/home/(?!runner\b)[^\s"<>]+)'
  'account-id-review' = '(?<!\d)\d{12}(?!\d)'
  'aws-endpoint-review' = '(?i)(?:[a-z0-9.-]+\.(?:elb\.amazonaws\.com|rds\.amazonaws\.com|cache\.amazonaws\.com)|internal-[a-z0-9.-]+)'
  'cloudfront-public-not-secret' = '(?i)[a-z0-9]+\.cloudfront\.net'
  'email-review' = '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b'
  'credential-marker-review' = '(?i)(?:AKIA|ASIA)[A-Z0-9]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|(?:password|secret_access_key|session_token|authorization)\s*[=:]\s*["'']?[^\s,"''}]{8,}'
}
$files = @()
$findings = @()
foreach ($path in $paths) {
  $full = Join-Path $root $path
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { throw "Missing allowlisted file: $path" }
  $item = Get-Item -LiteralPath $full
  if ($item.LinkType) { throw "Refusing linked allowlisted file: $path" }
  $files += [ordered]@{ path=$path; bytes=$item.Length; sha256=(Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant() }
  $text = [IO.File]::ReadAllText($full)
  foreach ($category in $patterns.Keys) {
    $count = [regex]::Matches($text,$patterns[$category]).Count
    if ($count) { $findings += [pscustomobject][ordered]@{path=$path; category=$category; count=$count} }
  }
}
$utf8 = [Text.UTF8Encoding]::new($false)
$list = ($paths -join "`n") + "`n"
[IO.File]::WriteAllText((Join-Path $out 'prospective-sharelist.txt'),$list,$utf8)
$listHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($list))).ToLowerInvariant()
$snapshot = [ordered]@{ utc=[DateTime]::UtcNow.ToString('o'); files=$files; count=$paths.Count; sharelistSha256=$listHash; hashConvention='SHA-256 of UTF-8 no-BOM sorted repository-relative paths, LF-separated with terminal LF; file hashes are working-tree bytes'; findings=$findings; limitations=@('Heuristic category matches require review; not a credential/PII absence guarantee.','Only explicit prospective allowlist; no history, broader index, stashes, private sessions, raw payloads or report drafts.','No source edits, builds, host Node/Java, AWS calls, traffic or publication.') }
[IO.File]::WriteAllText((Join-Path $out 'snapshot-and-categories.json'),($snapshot | ConvertTo-Json -Depth 10),$utf8)
[ordered]@{count=$paths.Count; sharelistSha256=$listHash; categoryCounts=@($findings | Group-Object category | ForEach-Object { [ordered]@{category=$_.Name; files=$_.Count; occurrences=($_.Group | ForEach-Object { $_.count } | Measure-Object -Sum).Sum} })} | ConvertTo-Json -Depth 5
