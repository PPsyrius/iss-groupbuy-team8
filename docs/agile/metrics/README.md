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

Since 2026-09-18 `.github/workflows/board-snapshot.yml` writes the day's file at 23:30 Singapore
and collects the unmerged days in one open PR from `metrics/board-snapshots`; merge it whenever.
A file whose board columns are all blank came from a run without the `BOARD_SNAPSHOT_TOKEN`
secret (issue state only; burndown falls back to `state = OPEN`, as above).
