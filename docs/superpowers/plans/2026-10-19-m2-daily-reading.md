# M2 Daily Reading Implementation Plan

> **For agentic workers:** Use focused parallel workers for independent text and library domains, then integrate and review the whole change once. Steps use checkbox syntax. The user authorized implementation and reduced repeated testing in this session.

**Goal:** Deliver M2 daily TXT reading and complete user-controlled storage removal without losing the M1.5 library.

**Architecture:** Keep three lightweight Gradle modules. Extract navigation/input/reading settings from the activity. Independent library and text workers own disjoint files; the main implementer owns integration and device runs.

**Tech Stack:** Java 8, Android Views/SQLite, minSdk 27, compile/target 35, Gradle 8.7/JDK17.

**Spec:** `docs/superpowers/specs/2026-10-19-m2-daily-reading-design.md`

## Global Constraints

- Release `0.3.0-m2`, versionCode 5, existing `com.sh02l.reader` ID and certificate.
- Preserve user books; no production uninstall/clear-data, no external push or publish.
- No AndroidX, NDK, network runtime dependencies or EPUB in this iteration.
- Literal case-sensitive search, 100 results/batch; library 50 records/batch; emergency reserve 256 KiB.
- Deletion touches only explicitly selected app-private books/covers or selected files in app import directory; source files elsewhere are retained.
- User approved fewer tests: concentrate on data safety and input/locator behavior; no repeated visual matrices or M3 endurance run.

## Review Focus

- In-flight progress/result callbacks after deleting a book must not restore rows or display stale text.
- Failed/aborted cache replacement must preserve a readable original and recover consistently after restart.
- Input keys including 0, digits and arrows must reach input/IME without opening help or turning pages.
- Import cleanup must reject path traversal/symlinks and leave external Books sources untouched.
- Low-storage retry must retain only the newest failed position and avoid loops.

## Task 1: Baseline and implementation record

- [x] Preserve M1.5 work as local commit and tag; create feature branch.
- [ ] Run baseline JVM/build/lint once; keep evidence in `tools/logs/m2-baseline.txt`.
- [ ] Keep progress in `docs/M2_PROGRESS.md`; record interfaces and verification as work proceeds.

## Task 2: Library lifecycle and storage (independent worker)

**Files:** `BookRepository.java`, `CoverStore.java`, `ProgressWriter.java`, new `StorageManager.java`, focused library instrumentation tests.

**Produces:** `BookRepository.listPage(offset,limit)`, `count()`, `pageOf(id,size)`, `renameBook(id,title)`, `deleteBook(id)`, `removeBookmark(id,offset)`, `renameBookmark(id,offset,label)`, `ensureCache(id,observer)`, `rebuild(id,charset,observer)`; `CoverStore.remove(id)`; storage API specified in task brief.

- [ ] Add focused deletion/upgrade/recovery tests, observe missing behavior before implementation where possible.
- [ ] Implement v2 index migration, paged metadata and management, crash-safe book deletion and cache rebuild, validated source retention.
- [ ] Implement storage scan/cache/import cleanup and allocated reserve, retained failed progress with bounded retry.
- [ ] Report exact APIs and targeted evidence; root performs shared Android build/device run.

## Task 3: Text search and TXT style boundary (independent worker)

**Files:** new `TextSearcher.java` and JVM tests, `TxtPaginator.java`, new TXT document-session interface/adapter as specified in brief.

- [ ] Write search tests for cross-8192-unit match, cancellation and bounded batches; observe RED.
- [ ] Implement streaming/bounded search with snippets, continuation offset, no whole-book read.
- [ ] Add configurable typeface to layout spec, retain existing constructor; expose a TXT session adapter behind a document-session contract.
- [ ] Run targeted JVM tests and report exact APIs.

## Task 4: Native UI, navigation and settings integration

**Files:** `MainActivity.java`, new `NavigationState.java`, `ReaderInputView.java`, `ReadingSettings.java`, optional controller/feature views; representative native tests.

- [ ] Extract navigation state and reading settings, preserve existing measured key lifecycle.
- [ ] Add native text/number input, search/continuation, jump and encoding workflows with cancellation tokens.
- [ ] Add book/bookmark rename/delete and cover removal confirmation; paged shelf; storage multi-select and import cleanup.
- [ ] Wire margin/line-height/typeface import and anchor-preserving relayout, automatic cache repair and progress retry on space recovery.
- [ ] Add representative input, storage and location regression coverage; avoid visual matrix duplication.

## Task 5: Verify, review and deliver

- [ ] Build debug/test and release, run JVM suite and targeted native tests on an explicit device.
- [ ] Request one independent whole-change review focused on data lifecycle/input/stale tasks; fix substantial findings with covering checks.
- [ ] Produce versioned signed candidate with hashes/source record; preserve previous APKs.
- [ ] If device is usable, cover-install release and perform short data-preserving smoke. Record actual limitations, update README/HANDOFF/M2 validation.
- [ ] Commit final sources and report APK, meaningful checks and remaining manual trial items.
