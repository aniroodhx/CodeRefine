# CodeRefine - Scaling Notes

How CodeRefine behaves as the target codebase grows, where it breaks, and the
path to monorepo scale. This documents the design thinking; parts are
implemented, parts are deliberately deferred with a plan.

## Where it is today

The current pipeline is tuned for a single service / small-to-medium repo:

- Scan: AstScanner walks the tree with Files.walk and parses each file once
  with JavaParser into an in-memory ParsedProject (a Map<Path, CompilationUnit>).
  This is the one real cost centre.
- Detect: every registered Detector runs over the shared parse result, so
  parsing is paid once regardless of detector count.
- Patch/verify: per-issue, and gated behind detection - an LLM call and a
  container only happen when a real issue is found.

Empirically this scans a ~40 file service in well under a second. The design
question is what happens at 10k, 100k, 500k files.

## Bottlenecks, in the order they bite

### 1. Parsing is single-threaded
AstScanner parses files sequentially on one thread. Parsing dominates scan time,
so this is the first wall. Fix: parse in parallel - the work is embarrassingly
parallel (each file is independent). A bounded executor over the file list,
collecting into a concurrent map, turns this into an N-core speedup with a
localized change. A per-thread JavaParser instance avoids the shared static
config in StaticJavaParser.

### 2. The whole AST is held in memory at once
ParsedProject keeps every CompilationUnit in a map for the duration of the run.
At ~10-50 KB of AST per file, 500k files is tens of GB - it won't fit. Fix:
stream instead of collect. Detectors don't need the whole project at once; N+1
and unbounded detection are per-file (entity resolution is the only cross-file
need). Restructure to: (a) a cheap first pass that extracts just the
entity/relationship map, then (b) a streaming pass that parses one file, runs
detectors, emits issues, and releases the AST. Memory becomes O(one file), not
O(repo).

### 3. Re-analyzing unchanged files every run
A CI run today re-parses everything even if one file changed. Fix: incremental
analysis keyed on content hash. Cache (file hash -> issues); on a re-run, only
re-analyze files whose hash changed, plus files that depend on a changed entity.
In a PR context this is the difference between scanning the diff (seconds) and
the whole monorepo (minutes).

### 4. Cross-file entity resolution doesn't scale as a full graph
Detection needs to know which types are @Entity and their fetch types. Today
that map is rebuilt per run. At monorepo scale this wants to be a persisted
symbol index (entities, repositories, their relationships) updated
incrementally - the same idea as an IDE's project index - so a run resolves
against the index instead of re-deriving it.

## Target architecture at monorepo scale

```
  changed files -> hash check -> parse (parallel, per-thread parser)
                       |                    |
                  cache hit?           streaming detect
                       |                    |  (AST released per file)
                       v                    v
                skip unchanged        issues -> patch -> verify
                                                 |
                         persisted entity/symbol index (incremental)
```

Key properties:
- Parallel parse across cores (bottleneck #1)
- Streaming so memory is O(file) not O(repo) (#2)
- Incremental - hash-gated, analyze only what changed (#3)
- Indexed entity/symbol resolution, updated incrementally (#4)

## What is deliberately NOT built yet, and why

All four fixes are localized to the scan/analyze layer - the Detector and
VerificationStrategy interfaces don't change. That's intentional: the pluggable
architecture means scale work is an infrastructure swap under a stable contract,
not a rewrite. It's deferred because current targets (single services, PR-sized
diffs) don't need it, and building it before there's a monorepo to test against
would be speculative. The point of this doc is that the growth path is understood
and the seams are already in the right places.

## Verification at scale

Layer 3 spins up a Postgres container per verified issue. At scale that's the
expensive step, not detection. Mitigations: reuse one container across a run's
verifications (schema-per-issue), cap concurrent containers, and - since
detection is cheap and deterministic - make verification opt-in for CI
(detect on every PR, verify on request or nightly).
