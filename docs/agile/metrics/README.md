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

Once `.github/workflows/board-snapshot.yml` is merged into `dev`, it will write the day's file at
23:30 Singapore and collect the unmerged days in one open PR from `metrics/board-snapshots`.
Merge it whenever. All-blank board columns mean that no queried board item matched the repository
issues. This commonly occurs when the project query is unauthorized, including when
`BOARD_SNAPSHOT_TOKEN` is absent, but can also occur when a successful query returns no matching
items. Check the workflow log for `warning: board not readable` to identify a query failure;
burndown falls back to `state = OPEN`, as above.
