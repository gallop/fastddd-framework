# Contributing to FastDDD Framework

## Before opening a change

- Keep public Maven coordinates and Java API compatibility unless the change is explicitly scheduled for a new release candidate.
- Use Java 21 and run the affected module tests. Run `./mvnw.cmd -B clean verify` before proposing a release-facing change.
- Add or update independent-consumer coverage when changing auto-configuration, optional dependencies, configuration properties, generated contracts, or public SPI.
- Use Conventional Commit prefixes with a Chinese descriptive subject.

## Pull requests

- Keep one concern per pull request and describe user-visible or compatibility impact.
- Do not submit credentials, customer data, proprietary Studio code, or copied third-party code without an auditable license source.
- The deterministic Outbox concurrency gate and all required CI checks must pass.

## Contribution flow

All changes land on `main` through pull requests:

1. Branch from `main` (`feat/...`, `fix/...`, `docs/...`).
2. Open a pull request — the **PR Validation** workflow runs against the merge preview: the full unit suite, the deterministic Outbox concurrency gate, and packaging.
3. Merge as a **merge commit** once **Validate Pull Request** passes; merging triggers the full build-and-test and security workflows on `main`.

Direct pushes to `main` are reserved for emergency hotfixes; branch protection enforces the pull-request requirement once the repository is public.

## Reporting security issues

Do not report unpatched vulnerabilities in public issues. Follow [`SECURITY.md`](SECURITY.md) and use its private reporting channel.
