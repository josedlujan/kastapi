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

## Deprecation policy

Before 1.0 we still change the API, but we do it in steps so that upgrading is never a surprise:

- A replacement ships first. The old form is marked `@Deprecated` at `WARNING` level in that same release, with a `ReplaceWith` when possible, and keeps working with the same JVM signature.
- In the next minor release the level goes to `ERROR`.
- The release after that removes it.
- Every deprecation and removal is listed in the `CHANGELOG`.

`compat/binary-0.1` guards the first step: a consumer compiled against 0.1.1 from Maven Central has to keep linking and passing with the jars built from the current checkout. Do not delete or change a signature it uses before the removal schedule says so.

## Useful commands

```bash
./gradlew build                  # compile, tests and checks
./gradlew ktlintFormat           # fix the style
./gradlew apiCheck               # public API compatibility
./gradlew assemble && ./gradlew -p compat/binary-0.1 test -PkastapiVersion=<version>   # 0.1 binary compatibility
./gradlew publishToMavenLocal    # install the modules to try them in another project
./gradlew :examples:hello:run    # example at http://localhost:8080
```

## Releasing (maintainers)

Artifacts are published to Maven Central through the Central Portal with the
[vanniktech plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/). The `dev.kastapi`
namespace is verified with a DNS record on `kastapi.dev`.

Secrets never go in the repository. Put them in `~/.gradle/gradle.properties` (or in `ORG_GRADLE_PROJECT_*`
environment variables):

```properties
mavenCentralUsername=<Central Portal user token name>
mavenCentralPassword=<Central Portal user token password>
signingInMemoryKey=<output of: gpg --export-secret-keys --armor KEY_ID, as one line with \n for line breaks>
signingInMemoryKeyId=<last 8 characters of the key ID>
signingInMemoryKeyPassword=<key passphrase>
```

Artifacts are only signed when a signing key is configured, so normal builds and CI need no secrets.

1. Update `CHANGELOG.md`, set the version in `build.gradle.kts` and merge the PR.
2. Tag the release (`git tag -a vX.Y.Z`) on the merged commit and push the tag.
3. From that tag, run `./gradlew publishToMavenCentral`. This uploads the signed artifacts as a deployment.
4. Review the deployment in the [Central Portal](https://central.sonatype.com/publishing/deployments) and press
   **Publish**. A published version is immutable: it cannot be deleted or changed.
5. Create the GitHub Release from the same tag.
