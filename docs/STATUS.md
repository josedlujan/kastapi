# Project status

Last updated: version 0.1.0.

## Why 0.1.0 and not 1.0

The number follows [Semantic Versioning](https://semver.org/): a `0.x` version means initial development, and the public API may change between minor versions. `1.0.0` will be the commitment to keep the API stable, and the conditions are not met yet:

- There is no external usage feedback yet.
- It is not published on Maven Central (today it is installed with `./gradlew publishToMavenLocal`).
- There are known limitations (see below).
- Features planned in the [roadmap](ROADMAP.md) are missing: dependency injection, authentication, generation with KSP and a multiplatform client.

## What 0.1.0 includes

Described in the [reference](reference.md): a typed DSL on top of Ktor, reading path, query and body from a `data class`, validation with annotations, errors in a single format, route checks at startup, OpenAPI 3.1, Swagger UI and the `kastapi-test` module. The detail of each change is in the [CHANGELOG](../CHANGELOG.md).

## Known limitations

Ordered from most to least important.

| Limitation | Effect | What would be needed |
| --- | --- | --- |
| Swagger UI is tied to version 5.34.1 from the CDN | It was verified in Chrome (the interface loads and shows the example's operations), but if `swagger-ui-dist` is updated the SRI hashes must be recomputed | An automated browser test is not planned |
| `@Pattern` does not protect against catastrophic backtracking | A badly written regex can block a thread on a hostile input | Document it (done in the guide) or add a time limit |
| `Content-Type: application/json` is not required | A body with another content type is still interpreted as JSON | Decide whether to respond `415` or stay permissive |
| The `/docs` interface depends on a CDN | Without an internet connection it is not shown; `/openapi.json` is still served | Serve the resources from the application itself (changes the dependency policy) |
| `kastapi-core` does not include logging (SLF4J) | Without an implementation on the classpath, the `500` error log entry is not written | It is documented; the example uses logback |
| Only the `422` response is documented in OpenAPI | `400`, `404`, `405`, `413` and `500` do not appear | Documented multiple responses (planned for 0.2) |
| `apiCheck` does not cover the DSL's `inline reified` functions | A change in `get`, `post`... is not detected by the tool | Compiling the example and the tests covers it in part |
| The `405` does not include the `Allow` header | Less informative for the client | Compute the allowed methods per route |
| An own `StatusPages` must be installed before `installKastApi` | Installing it afterwards throws `DuplicatePluginException` | It is documented |
| `sealed`, polymorphic and contextual types are documented as `{}` | Open schema in OpenAPI | Support for `oneOf` and discriminators |
| Default values, `Set` and `Map` keys do not have full support in the schema | Minor details of the document | Extend the schema generator |
| Error messages are in English only | No internationalization | Planned for 0.2 |
| The maximum depth of the body's JSON has not been verified | Possible stack consumption with extreme nesting | Test with deeply nested inputs and, if needed, a limit |
| Classes shared between modules are public with `@InternalKastApi` | They are public in the bytecode, although they are not part of the stable API (`apiCheck` excludes them) | Review before 1.0 |
| Part of the documentation, code comments and test names may still be in Spanish | Mixed languages while the translation is completed | Translation contributions are welcome |

## What is missing

### To close the 0.1.0 release

- [x] Code, tests, public documentation and guide.
- [x] Manual Swagger UI test in a browser (Chrome).
- [x] Tag `v0.1.0`.

### For 0.2

Entry condition in the roadmap: 0.1 published and first users. Planned content: injectable dependencies, typed headers and cookies, documented multiple responses and internationalization of the messages.

### For 1.0

Authentication integrated with OpenAPI, freezing the API, publishing on Maven Central with signing and javadoc, and a documentation site. Condition: adoption feedback. Before freezing the API, the visibility of `InternalKastApi` and the open decisions in the table above should be resolved.

## How decisions are made

- Changes to the public API are discussed in an issue and reflected in the [reference](reference.md).
- Design decisions with alternatives are recorded as ADRs in [`docs/adr`](adr).
- To suggest a change, open an issue with the proposal template (see [CONTRIBUTING](../CONTRIBUTING.md)).
