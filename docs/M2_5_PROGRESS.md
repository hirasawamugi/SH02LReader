# M2.5 execution ledger

Plan: `docs/superpowers/plans/2026-10-20-m2-5-optimization-epub.md`

- Authorized: user said 开始吧; implement inline, architecture first, one integrated delivery, focused checks then one integrated review/validation.
- Baseline: `f52018d`, branch `feature/m25-optimization-epub`; current checkout reused, no additional SDK/cache/worktree requested.
- Pre-flight: key map/list policies feed input/menu; core locations feed persistence and Android surface contracts; engines depend on reader-android/core, never app. Publication checks must include both new modules and M2_5 documents.
- Ruling: use this tracked ledger rather than shell-specific SDD helpers, retaining task status and decisions across compaction. Existing feature branch is isolated from M2/master without creating another checkout.
- Task 1: in progress — meaningful mapper/cycle and list boundary checks before implementation.
- Task 1: core mapper/cycle and circular selection implemented; missing-type RED observed in m25-core-red.txt, then 55 JVM cases passed (m25-core-green.txt, 26s). Production UI wiring follows the shared controllers.
