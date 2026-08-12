# Sample: blog-app

A minimal Spring Data JPA project with a **deliberate N+1 query bug**, used to
demo and smoke-test CodeRefine locally.

## The bug

`BlogService.summarizeAllAuthors()` loads all authors in one query, then calls
`author.getPosts()` inside a loop. Because `Author.posts` is a lazy
`@OneToMany`, each iteration fires a separate SQL query:

- 1 query for the authors
- N queries for each author's posts
- => **N+1 queries**

## Run CodeRefine against it

From the repo root (where `.env` lives):

```bash
./gradlew :coderefine-cli:bootJar
java -jar coderefine-cli/build/libs/coderefine-cli-0.1.0-SNAPSHOT.jar samples/blog-app
```

Expected: CodeRefine detects the N+1 on `Author.posts`, asks Gemini for a
JOIN FETCH / @EntityGraph fix, verifies it in a Postgres sandbox, and reports
the query-count reduction.

To detect only (skip the Docker sandbox and LLM call), you can inspect Layer 1
behavior via the unit tests in `coderefine-core`.
