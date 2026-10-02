# Contributing

Solo project, but the workflow is real so it holds up if that changes.

## Branch naming

```
feat/#{issue-number}-short-description
fix/#{issue-number}-short-description
chore/#{issue-number}-short-description
```

Reference the GitHub issue number in every branch name and every commit.

## Commit format

[Conventional Commits](https://www.conventionalcommits.org/), plus an issue-closing footer:

```
feat(ble): add ISO15693 NFC unlock layer for Libre 2

closes #12
```

A plain `closes #N` auto-closes the issue on merge to `main`.

## Issues & milestones

- One GitHub issue per task in `docs/PLAN.md` §9's phased roadmap.
- Milestones map 1:1 to phases (`Phase 0 — Foundation`, `Phase 1 — Core BLE + usable app`, etc.).
- The same task list is also tracked in Akiflow for personal scheduling — GitHub issues are the source of truth for *what* the work is; Akiflow is just when it gets done.

## Pull requests

Every feature/fix branch gets a PR against `main`, even solo — it's the changelog. Merge is a manual, deliberate step (not auto-merged), since some of this work (Libre BLE decoding, the emergency-call escalation) is safety-relevant and worth a second look before it lands.

## No AI attribution

Commits, PR descriptions, and code comments stay free of any AI-tool attribution ("Generated with...", `Co-Authored-By: Claude`, etc.) — a hard rule, not a style preference.

## A note on the Libre BLE work specifically

Sensor decoding is ported from **MIT-licensed** projects only (GlucoseDirect, DiaBLE, LibreTools — keep `THIRD_PARTY_NOTICES.md` current when porting more). Never copy code from xDrip+, Juggluco, or other GPL-licensed projects (see `docs/PLAN.md` §1).
