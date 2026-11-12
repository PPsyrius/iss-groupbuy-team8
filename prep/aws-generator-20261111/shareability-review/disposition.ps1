$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$out = Join-Path $root 'prep/aws-generator-20261111/shareability-review'
$s = Get-Content -LiteralPath (Join-Path $out 'snapshot-and-categories.json') -Raw | ConvertFrom-Json
$hard = @('docs/swe5001/evidence/20261111-v3-r3/prelaunch/prechecks/target-readiness-refusal-followup.json','docs/swe5001/evidence/20261111-v3-r3/checkpoint/minimal-collection-integration/INTEGRATION.md')
$review = @($s.findings | Where-Object { $_.category -ne 'cloudfront-public-not-secret' } | Select-Object -ExpandProperty path -Unique)
$items = @($s.files | ForEach-Object {
  $p=$_.path
  $status=if($p -in $hard){'BLOCKED-REDACTION-OR-EXCLUSION'}elseif($p -in $review){'PARTIAL-OPERATOR-REVIEW'}else{'READY-FOR-DRAFT-CANDIDATE'}
  [ordered]@{path=$p;status=$status;categories=@($s.findings | Where-Object path -eq $p | Select-Object -ExpandProperty category);sha256=$_.sha256}
})
$utf8=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $out 'file-dispositions.json'),($items | ConvertTo-Json -Depth 6),$utf8)
$ready=@($items | Where-Object { $_['status'] -eq 'READY-FOR-DRAFT-CANDIDATE' } | ForEach-Object { $_['path'] })
[IO.File]::WriteAllText((Join-Path $out 'ready-candidates.txt'),(($ready -join "`n")+"`n"),$utf8)
$changed=@($s.files | Where-Object { (Get-FileHash -LiteralPath (Join-Path $root $_.path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $_.sha256 } | Select-Object -ExpandProperty path)
$current=@(Get-ChildItem -LiteralPath (Join-Path $root 'docs/swe5001/evidence/20261111-v3-r3') -Recurse -File | ForEach-Object { [IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/') })
$new=@($current | Where-Object { $_ -notin $s.files.path })
$receipt=[ordered]@{utc=[DateTime]::UtcNow.ToString('o');prospectiveCount=$s.count;readyCandidateCount=$ready.Count;blockedCount=$hard.Count;operatorReviewCount=$s.count-$ready.Count-$hard.Count;sharelistSha256=$s.sharelistSha256;changedPaths=$changed;newPaths=$new;stable=($changed.Count -eq 0 -and $new.Count -eq 0);warning='Candidates require the report DRAFT warning and explicit owner selection; no publication approval or whole-index approval.'}
[IO.File]::WriteAllText((Join-Path $out 'stability-and-counts.json'),($receipt|ConvertTo-Json -Depth 5),$utf8)
$receipt|ConvertTo-Json -Depth 5
