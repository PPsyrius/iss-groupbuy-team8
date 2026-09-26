# Board and issue snapshots

One CSV per day, written by `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh`.
Columns: snapshot date, issue number, title, state, board status, labels, assignees, size,
estimate, priority, milestone, created and closed timestamps.

Burndown for a sprint: for each snapshot date in the sprint window, count rows whose board status
is not `Done` (or whose state is `OPEN` if the board columns are blank) among the sprint's items.
Plot count against date.

The board UI is not in the git archive, so these files are the only durable record of card
movement. Run the script at the end of any session that changed issue or board state, and commit
the CSV with that day's work.

The scheduled `.github/workflows/board-snapshot.yml` collects unmerged daily files in one open PR from `metrics/board-snapshots`; the cron targets 23:30 Singapore, but GitHub may start scheduled runs late enough to write the following Singapore day's file. Merge the PR whenever convenient.
The run requires `BOARD_SNAPSHOT_TOKEN` with repository contents/PR write, Issues read, `read:project`, and access to `users/PPsyrius/projects/2`. A missing token, failed project query, or board with no status-bearing repository issues fails the run before it writes or publishes a CSV. Refreshing local `gh` authentication does not change the repository secret; see `docs/ci/devsecops-pipeline-plan.md` off-repo TODO 8.
Snapshots dated 2026-09-19 through 2026-09-24 were originally published with blank board columns after the workflow's project query failed; their board statuses were reconstructed from the project's dated issue timeline events at each run's capture time. Issue #22 legitimately has no board card and keeps a blank status; for individual unboarded issues, burndown falls back to `state = OPEN` as above.
