# Board and issue snapshots

One CSV per day, written by `.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh`.
Columns: snapshot date, issue number, title, state, board status, labels, assignees, size,
estimate, priority, milestone, created and closed timestamps.

Burndown for a sprint: for each snapshot date in the sprint window, count rows whose board status
is not `Done` (or whose state is `OPEN` if the board columns are blank) among the sprint's items.
Plot count against date.

Hours burndown when no hours were logged (Sprints 3 and 4): list the sprint's backlog items with their planned hours and issues in `docs/agile/sprint-NN/burndown-items.csv`, then run `.claude/skills/agile-evidence-capture/scripts/burndown.py`. Each item keeps its planned hours while any of its issues is open in that day's snapshot; items without an issue are left out. It writes `burndown.csv` (with the board Estimate sum beside the result) and an SVG chart. Sprint 4: `python3 .claude/skills/agile-evidence-capture/scripts/burndown.py --items docs/agile/sprint-04/burndown-items.csv --metrics docs/agile/metrics --days 2026-10-12,2026-10-13,2026-10-14,2026-10-15,2026-10-16,2026-10-19,2026-10-20,2026-10-21,2026-10-22,2026-10-23 --csv docs/agile/sprint-04/burndown.csv --svg docs/agile/sprint-04/burndown.svg --title "Sprint 4 burndown" --subtitle "12-23 October 2026, remaining planned hours of issue-backed items (214.5 of 224.5 h)"`.

The board UI is not in the git archive, so these files are the only durable record of card
movement. Run the script at the end of any session that changed issue or board state, and commit
the CSV with that day's work.

The scheduled `.github/workflows/board-snapshot.yml` collects unmerged daily files in one open PR from `metrics/board-snapshots`; the cron targets 23:30 Singapore, but GitHub may start scheduled runs late enough to write the following Singapore day's file. Merge the PR whenever convenient.
The run requires `BOARD_SNAPSHOT_TOKEN` with repository contents/PR write, Issues read, `read:project`, and access to `users/PPsyrius/projects/2`. A missing token, failed project query, or board with no status-bearing repository issues fails the run before it writes or publishes a CSV. Refreshing local `gh` authentication does not change the repository secret; see `docs/ci/devsecops-pipeline-plan.md` off-repo TODO 8.
Snapshots dated 2026-09-19 through 2026-09-24 were originally published with blank board columns after the workflow's project query failed; their board statuses were reconstructed from the project's dated issue timeline events at each run's capture time. Issue #22 legitimately has no board card and keeps a blank status; for individual unboarded issues, burndown falls back to `state = OPEN` as above.
