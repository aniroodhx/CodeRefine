# CodeRefine

I built this because AI coding tools (Cursor, Copilot, Claude) make it really easy to ship code fast — and really easy to ship subtle performance bugs along with it. The kind that aren't syntax errors, so they sail through code review, pass your tests on 5 rows of dev data, and then quietly hammer your database in production.

The one I kept running into is the N+1 query — a loop that fires one SQL query per iteration instead of a single batched one. So CodeRefine finds those, asks an LLM to fix them, and then actually proves the fix works before trusting it.

That last part is the whole point. Most tools stop at "here's a suggestion." This one doesn't accept a fix unless it can show the query count actually dropped.

## How it works

Three layers, and nothing gets through without proof:

**Layer 1 — find the bug (no AI yet).** JavaParser walks the code and flags N+1 patterns locally. This is deterministic and free, and it means I'm not shipping your whole repo off to an LLM — only the flagged method gets sent, and only if there's actually something to fix. Cuts token cost by roughly 85%.

**Layer 2 — fix it.** Only the relevant entity + service + repository go to Gemini (not the whole file, definitely not the whole repo). It comes back as structured JSON — a real patch (JOIN FETCH / @EntityGraph), not a vague "you might want to..." suggestion.

**Layer 3 — prove it.** This is the part I care about most. It spins up a real Postgres in Docker (via Testcontainers), runs the code before and after the patch, and counts the actual SQL queries. If the patch doesn't reduce queries, or it breaks something, it gets rejected. Only fixes that measurably help get approved.

## What it looks like

Running it against the sample project I included (it has a deliberate N+1 bug):

    $ java -jar coderefine-cli/build/libs/coderefine-cli-0.1.0-SNAPSHOT.jar samples/blog-app

    Layer 1: Analyzing samples/blog-app for N+1 patterns...
    Detected 1 N+1 issue(s)
    Processing: [N+1] BlogService.summarizeAllAuthors (line 27): lazy field 'posts' of 'Author' inside for-each loop
    Layer 2: Generating patch via LLM...
    Layer 3: Verifying patch in sandbox...
    Verdict: APPROVED — Query count reduced from 11 to 1 (91% improvement)

      Issues detected:    1
      Fixes approved:     1
      Queries saved:      10

11 queries down to 1. That's the thing it proves, not just claims.

## Running it yourself

You'll need JDK 17+, Docker running (for the sandbox), and a Gemini API key.

    cp .env.example .env        # then put your GEMINI_API_KEY in .env
    ./gradlew :coderefine-cli:bootJar
    java -jar coderefine-cli/build/libs/coderefine-cli-0.1.0-SNAPSHOT.jar <path-to-project>

Add --no-verify if you want detection + patch only and don't want to bother with Docker. Run the tests with ./gradlew test.

Heads up on Docker: newer Docker daemons reject the old default API version the Docker client uses, so I pin it to 1.44 (via the api.version system property in the Gradle test task). If you run the built jar and hit that error, drop api.version=1.44 into ~/.testcontainers.properties. Took me a while to figure that one out.

## Layout

    coderefine-core/     Layer 1 — finds the N+1s (JavaParser)
    coderefine-llm/      Layer 2 — talks to Gemini, gets a structured patch back
    coderefine-verify/   Layer 3 — the Postgres sandbox that counts queries
    coderefine-cli/      ties all three together into one command
    samples/blog-app/    a little Spring app with a bug on purpose, to test against

Built with Java 17, Spring Boot 3.3, JavaParser, the Gemini API, Testcontainers, Postgres, and Gradle.

## Where it's at

Right now (V1): N+1 detection on Spring Data JPA, working end to end. It handles for-each / for / while loops, streams, method references and lambdas, respects JPA fetch types, and tells you honestly if it couldn't parse some files instead of pretending it scanned everything.

Next:
- V2 — more detectors (unbounded findAll() with no pagination, queries on unindexed columns), and a proper pluggable detector setup so adding new ones is clean.
- V3 — make it something you can actually drop into CI or a GitHub workflow.

## Quick refresher on why N+1 is bad

A lazy JPA relationship doesn't load until you touch it. Touch it inside a loop and you get one query every time around:

    List<Author> authors = authorRepository.findAll();  // 1 query
    for (Author a : authors) {
        a.getPosts().size();   // one more query, every single loop
    }

100 authors = 101 queries when it should've been 2. Fine in dev, brutal in prod. That's exactly the gap CodeRefine is meant to close.
