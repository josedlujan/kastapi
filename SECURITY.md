# Security policy

## Supported versions

KastAPI is at version 0.1.1 and is experimental. Only the latest published version receives security fixes.

## How to report a vulnerability

Do not open a public issue. Use GitHub's private reporting: the **Security** tab of the repository, then **Report a vulnerability**. If it is not available, write to josedlujan@hotmail.com with the subject "KastAPI: security".

Include, if you can:

- The KastAPI, Kotlin and Ktor versions.
- Minimal code to reproduce it.
- What you expected and what happened.

I will reply within a reasonable time and let you know when a fix is published.

## What counts as a vulnerability

For example: error responses that leak internal information, inputs that consume disproportionate resources (such as regular expressions or unbounded bodies), or ways to bypass the declared validation.

The known limitations are listed in [docs/STATUS.md](docs/STATUS.md). In particular, `@Pattern` uses the regular expression written by the developer: a pattern with catastrophic backtracking is that developer's responsibility.
