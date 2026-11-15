# Contributing to KastAPI

Thanks for your interest!

## Before you start

- For big changes or changes to the public API, open an issue first, or write an ADR (`docs/adr/TEMPLATE.md`).
- Look for issues labeled `good first issue` if this is your first contribution.

## Translations

KastAPI was designed and first written in Spanish, and then translated to English. Some documentation, KDoc comments, code comments or test names may still be in Spanish. Translating them, or fixing an awkward sentence, is a welcome contribution: open an issue or send a pull request. Keep these in mind:

- The Spanish front page is `README.es.md`. Everything else should end up in English.
- Do not translate identifiers, error `code` values or anything that changes behavior without opening an issue first.

## Environment

- JDK 21
- `./gradlew build` must pass before you open a PR.

## Style

- Official Kotlin style + ktlint (`./gradlew ktlintFormat`).
- `explicitApi()`: declare visibility explicitly.
- KDoc on everything public.
- Conventional Commits.

## Pull requests

- One thing per PR.
- Include tests.
- Update `CHANGELOG.md` under `Unreleased`.
- If you change the public API, run `./gradlew apiDump` and explain the change.

## Useful commands

```bash
./gradlew build                  # compile, tests and checks
./gradlew ktlintFormat           # fix the style
./gradlew apiCheck               # public API compatibility
./gradlew publishToMavenLocal    # install the modules to try them in another project
./gradlew :examples:hello:run    # example at http://localhost:8080
```
