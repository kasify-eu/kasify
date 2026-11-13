# Contributing to Kasify

Thanks for your interest in improving Kasify! Issues and pull requests are welcome.

## Licensing of contributions

Kasify is licensed under the [GNU AGPL v3.0](LICENSE). By submitting a pull request you agree that your contribution is licensed under the same terms (inbound = outbound). There is no CLA to sign. Only submit code you wrote yourself or that you have the right to contribute under the AGPL-3.0.

## Before you start

- For anything larger than a small fix, open an issue first so we can agree on the approach.
- Check `api/CLAUDE.md` and `dashboard/CLAUDE.md` for architecture notes and conventions.

## Development setup

See the [README](README.md#getting-started). The checks that run on every change:

```bash
cd api && ./gradlew test ktlintFormat
cd dashboard && npm run typecheck
```

### IntelliJ IDEA

`.idea/` is not versioned, so each contributor sets the project up once:

1. Open the repository root (not `api/`).
2. Right-click `api/build.gradle.kts` and choose **Link Gradle Project** (or **Reload All Gradle Projects** if it is already linked).
3. The Kotlin code targets JDK 21. Gradle downloads it through the toolchain, so any JDK 17+ is enough to run Gradle itself. Select it under *Settings → Build Tools → Gradle → Gradle JVM*.

Code style comes from `.editorconfig` and ktlint, so no shared IDE settings are needed.

## Guidelines

- Kotlin: ktlint style, no wildcard imports, all database queries must verify the caller's permissions through `property_access`.
- TypeScript: strict mode, `~/*` import alias, new API calls go in the `ApiClient` class, domain types live in `app/types/`.
- Add or update tests for behaviour changes. User-facing text must be translated in both `en` and `es` locale files.
- Keep pull requests focused: one change per PR.
- Never commit secrets, real guest data or recorded responses from real accounts. Use synthetic fixtures.

## Commits and pull requests

We use [Conventional Commits](https://www.conventionalcommits.org) and generate the changelog and releases from them with [release-please](https://github.com/googleapis/release-please).

Pull requests are **squash-merged**, and the **PR title becomes the commit message** on `main`. That means only the PR title has to follow the format (a CI check enforces it); the commits inside your branch can be as messy as you like.

```
type(scope): subject
```

- **type**: one of `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`, `revert`.
- **scope** (optional): the area you touch, e.g. `api`, `dashboard`, `docs`, `ses`, `nuki`, `calendar`.
- **subject**: imperative, lowercase first letter, no trailing period.

Examples:

```
feat(bookings): add VRBO iCal import
fix(ses): retry on 503 responses
docs: explain Kinde setup
feat(api)!: rename /v1/properties fields
```

A breaking change is marked with `!` after the type/scope, and explained in the PR description (a `BREAKING CHANGE:` line in the squash commit body is also picked up). `feat` entries produce a minor release, `fix` and `perf` a patch release, and a breaking change a major release (while below 1.0, a minor one). `docs`, `refactor`, `test`, `build`, `ci` and `chore` do not appear in the changelog.

Maintainers: merge release-please's "Release PR" to publish a version; do not edit `CHANGELOG.md` by hand. In the repository settings, allow only squash merging and set the default commit message to "Pull request title".
