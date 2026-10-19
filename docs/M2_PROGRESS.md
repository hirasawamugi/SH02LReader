# M2 execution ledger

Plan: `docs/superpowers/plans/2026-10-19-m2-daily-reading.md`

- User authorization: 2026-10-19 start M2, fewer repeated tests. Previous two answers' structural and storage-management recommendations accepted.
- Baseline: `f6417b5`, tag `m1.5-v0.2.6`, branch `feature/m2-daily-use`. M1.5 working changes preserved, main branch untouched.
- Approach: independent text and library domains may be delegated using dispatching-parallel-agents; root owns UI integration and all shared device operations. One final review, no per-task review loop.
- Task 1 complete: baseline verify BUILD SUCCESSFUL, 109 tasks, 1m28s (`tools/logs/m2-baseline.txt`).
- Text domain complete: 5 focused RED→GREEN search cases; 51 JVM cases pass; session and typeface API compiled.
- Library/storage implementation complete: 7 focused native methods supplied, frozen APIs preserved; pending final runtime verification.
- UI integration compiled/linted; extracted navigation/input/settings/feature workflows and wired data lifecycle.
- Initial native run 21 methods: 15 pass/6 fail. Five UI failures shared initial native selection/touch-mode root cause; one test compared Android path aliases. Fixes applied. Existing bookmark test updated for the intentional M2 action submenu.
- Integrated compile/lint and 51 JVM cases pass; 23 representative native cases running. One independent whole-change review running against `tools/logs/m2-review-package.diff`.
- Native integrated: emulator 23/23 pass; first phone 11/11 pass. Independent review identified two P2 lifecycle bugs, both reproduced in two focused failing native cases.
- Final fix pass: duplicates now verify original identity while retaining selected encoding/cache state; deletion invalidates live positions and pending/dequeued progress generations. Added controlled repository-lock ordering case.
- Final verify: 51 JVM cases, debug/test and Lint pass. Final SH-02L 14/14 cases pass (27.932s), including both RED→GREEN regressions and dequeued write invalidation.
- Formal pre-upgrade shelf observed 2 books, 大爱仙尊 at 0.9%, 遮天 at 0.0%; original Books hashes/metadata saved.
- UI QA: async placeholder panel retained its small height; fixed targeted refresh and loading text, version 0.3.1-m2/code6. Release/Lint passed; actual SH-02L storage panel visually verified. Earlier 0.3.0 candidate preserved.
- Release source `8d0df64`, APK 86,692 bytes, SHA256 dd065bfd81d99d8074537b6f6597f4353c79b26866d7c468485367a3bebaaf3e. Same certificate and installed bytes verified.
- Cover upgrade and force-stop/reopen preserve both books and displayed progress; external Books hashes unchanged. Own debug/test packages and remote temporary captures removed, emulator closed, crash buffer unchanged.
- Complete: implementation, focused tests, independent review and fixes, source commits, signed candidate, real-device upgrade/QA and cleanup. Local branch retained; M3/Chinese IME composition/long trial boundaries recorded in M2_VALIDATION.
