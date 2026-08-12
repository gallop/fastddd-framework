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

## Reporting security issues

Do not report unpatched vulnerabilities in public issues. A private reporting channel has not yet been established, so this repository is not ready for public release. The project owner must publish `SECURITY.md` with a private reporting channel before any public release.
