# CodeRefine — Design Notes

Architecture decisions and rationale, kept as the system evolves. This doubles
as the "why" behind the code for anyone reviewing it.

## The core idea

A three-layer pipeline where **no fix is accepted without proof**:

1. **Detect** (deterministic, local) — AST analysis flags anti-patterns. No LLM,
   no code leaves the machine unless something is actually found.
2. **Patch** (LLM) — only the minimal relevant context for one issue is sent to
   the model, which returns a structured patch.
3. **Verify** (sandbox) — the patch is run in a real Postgres (Testcontainers)
   and measured. Only measurable improvements are approved.

The verification layer is the differentiator. Most tools stop at "here's a
suggestion"; this one proves the suggestion works before trusting it.

## V2: pluggable detection and pluggable verification

The central V2 decision was to make **both ends of the pipeline extensible in a
symmetric way**, rather than special-casing each new anti-pattern.

### Detection side — `Detector`

- `AstScanner` walks the project and parses each file **once** into a shared
  `ParsedProject` (also tracks scan/parse-failure counts for honest coverage
  reporting).
- Each anti-pattern is a `Detector` implementation that reads the shared parse
  result and returns `Issue`s. Adding a detector is a one-line registration in
  `CodeRefineAnalyzer`.
- All detected issues implement a common `Issue` interface (file, class, method,
  line, type, description); each detector also has its own richer record
  (`NPlusOneIssue`, `UnboundedCollectionIssue`).

Detectors today: N+1 queries, unbounded collections (`findAll()` with no bound).

### Verification side — `VerificationStrategy`

The key insight: **different bugs need different proof, not the same metric.**

- N+1 is a *query-count* problem → `QueryCountStrategy` (queries: 11 → 1).
- Unbounded `findAll()` is a *row-count / memory* problem, and fires only one
  query — invisible to query counting. It needs `ResultSetSizeStrategy`
  (rows: 10,000 → 20).

Both run in the **same** Testcontainers Postgres sandbox; they differ only in
what they measure. `SandboxVerifier` picks the strategy by issue type, so the
sandbox infrastructure is written once and each anti-pattern declares how it is
proven. `VerificationResult` is metric-agnostic (`metricName` + before/after),
so the report renders any metric uniformly.

This mirrors the detection side: **pluggable detectors ↔ pluggable verifiers.**
Adding a new anti-pattern means adding a `Detector` + a `VerificationStrategy`,
not editing a growing pile of conditionals.

## Deliberate trade-offs

- **Conservative unbounded detection.** Only the no-argument `findAll()`/
  `readAll()`/`getAll()` on a repository-typed target is flagged. Any argument
  (Pageable, Sort, Example, id collection, …) is treated as intentional. This
  keeps the false-positive rate low, which matters for trust.
- **Honest coverage.** If files fail to parse, that count is surfaced (not
  swallowed), so "scanned N repos" is never silently overstated.

## Known gaps / next

- **Scale story (not yet built):** currently a single-pass local CLI. A monorepo
  run would want parallel parsing, incremental analysis, and cached ASTs.
- **Detection precision/recall:** needs measurement against real repos to report
  a real false-positive rate.
- **Deeper detectors (backlog):** iterating a lazy collection itself; chained
  accessors (`a.getB().getC()` in a loop); unindexed column scans (requires
  cross-referencing query columns against `@Index`/`@Column` — the hardest one).
- **LLM patch application/verification for unbounded** is proven at the metric
  level (row-count sandbox); wiring the actual generated Pageable patch through
  `PatchApplier` end-to-end is exercised for N+1 and generalizes next.
