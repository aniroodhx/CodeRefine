# CodeRefine

I built this because AI coding tools (Cursor, Copilot, Claude) make it really easy to ship code fast — and really easy to ship subtle performance bugs along with it. The kind that aren't syntax errors, so they sail through code review, pass your tests on 5 rows of dev data, and then quietly hammer your database in production.

The ones I kept running into are N+1 queries (a loop that fires one SQL query per iteration instead of a single batched one) and unbounded reads (findAll() with no pagination, quietly loading an entire table into memory). So CodeRefine finds those, asks an LLM to fix them, and then actually proves the fix works before trusting it.

That last part is the whole point. Most tools stop at "here's a suggestion." This one doesn't accept a fix unless it can measure that things actually got better.

## How it works

Three layers, and nothing gets through without proof:

**Layer 1 — find the bug (no AI yet).** JavaParser walks the code and flags anti-patterns locally. This is deterministic and free, and it means I'm not shipping your whole repo off to an LLM — only the flagged method gets sent, and only if there's actually something to fix. Cuts token cost by roughly 85%.

**Layer 2 — fix it.** Only the relevant entity + service + repository go to Gemini (not the whole file, definitely not the whole repo). It comes back as structured JSON — a real patch (JOIN FETCH / @EntityGraph for N+1, pagination for unbounded reads), not a vague "you might want to..." suggestion.

**Layer 3 — prove it.** This is the part I care about most. It spins up a real Postgres in Docker (via Testcontainers), runs the code before and after the patch, and measures it. Each bug type is proven with the metric that fits it — query count for N+1, rows returned for unbounded reads. If the patch doesn't measurably help, or it breaks something, it gets rejected. Only fixes that prove themselves get approved.

## What it looks like

Running it against a real third-party Spring service (himanshubuyerteam/Food_order_Project) with four unbounded findAll() calls:

    Layer 1: Analyzing ... for performance anti-patterns...
    Detected 4 issue(s)
    Processing: [Unbounded] OrderServiceImpl.getAllOrders (line 86): 'foodRepository.findAll()' loads the whole table with no pagination
    Layer 2: Generating patch via LLM...
    Layer 3: Verifying patch in sandbox...
    Verdict: APPROVED — Rows reduced from 10000 to 20 (100% improvement)
    ...

      Issues detected:    4
      Fixes approved:     4
      Patch failures:     0

    -> 4 verified fix(es) accepted after proving measurable improvement.

Four real bugs, four LLM patches, all four verified in a real database before being trusted. Not claimed — proven.

## Running it yourself

You'll need JDK 17+, Docker running (for the sandbox), and a Gemini API key.

    cp .env.example .env        # then put your GEMINI_API_KEY in .env
    ./gradlew :coderefine-cli:bootJar
    java -jar coderefine-cli/build/libs/coderefine-cli-0.1.0-SNAPSHOT.jar <path-to-project>

Add --no-verify if you want detection + patch only and don't want to bother with Docker. Run the tests with ./gradlew test.

The Docker API version is pinned to 1.44 in code, so the jar is self-contained — newer Docker daemons reject the client's legacy default, and this avoids that with no extra setup.

## Layout

    coderefine-core/     Layer 1 — the scanner + pluggable detectors (JavaParser)
    coderefine-llm/      Layer 2 — talks to Gemini, gets a structured patch back
    coderefine-verify/   Layer 3 — the Postgres sandbox + pluggable verification strategies
    coderefine-cli/      ties all three together into one command
    samples/blog-app/    a little Spring app with bugs on purpose, to test against

Built with Java 17, Spring Boot 3.3, JavaParser, the Gemini API, Testcontainers, Postgres, and Gradle. See DESIGN.md for the architecture and SCALING.md for how it grows to monorepo scale.

## Where it's at

Working end to end on Spring Data JPA, validated against real open-source repos (zero false positives on well-optimized code like spring-petclinic; caught and verifiably fixed 4 real bugs in a third-party service).

Detectors today:
- N+1 queries — lazy relationship accessed in a for-each / for / while loop, a stream, method reference, or lambda. Respects JPA fetch types.
- Unbounded collections — no-argument findAll()/readAll()/getAll() on a repository, with no pagination.

The detection and verification sides are both pluggable: a new anti-pattern is a new Detector plus a new VerificationStrategy, with no changes to existing code. It also reports parse coverage honestly — if it couldn't parse some files, it says so rather than pretending it scanned everything, and it skips test sources so legitimate findAll() in tests isn't flagged.

Next:
- Cross-method N+1 detection (following a lazy access into a helper method called from the loop).
- Unindexed-column scan detection.
- Dropping it into CI / a GitHub workflow.

## Quick refresher on why these are bad

N+1 — a lazy JPA relationship doesn't load until you touch it. Touch it inside a loop and you get one query every time around:

    List<Author> authors = authorRepository.findAll();  // 1 query
    for (Author a : authors) {
        a.getPosts().size();   // one more query, every single loop
    }

100 authors = 101 queries when it should've been 2.

Unbounded read — repository.findAll() with no pagination pulls the entire table into memory. Fine with 10 rows in dev; an OOM waiting to happen once the table has millions. The fix is to page it (Pageable/Page).

Fine in dev, brutal in prod. That's exactly the gap CodeRefine is meant to close.
