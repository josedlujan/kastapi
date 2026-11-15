package dev.kastapi.openapi

import kotlinx.serialization.json.JsonPrimitive

private const val SWAGGER_UI_BASE = "https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.34.1"

// SRI hashes (sha384) of the pinned version's resources; the browser rejects a tampered file.
private const val CSS_INTEGRITY = "sha384-4c3vsE5f+trhFyHrFrLM1fFGUn531NIaC7dcqEWIJdoRQJ5CkJbSuSCwXLpaS1KF"
private const val BUNDLE_INTEGRITY = "sha384-XFYN47VzPiDY6qPtxYKg0nqUeW/upo20xf1j2SUE7gT/i35FJB4cuS/71Iegzk8Z"

/**
 * Swagger UI HTML: loads `swagger-ui-dist` from a CDN with a pinned version and points to [openApiPath].
 *
 * @param title page title.
 * @param openApiPath path from which the OpenAPI document is served.
 */
@InternalKastApi
public fun swaggerUiHtml(
    title: String,
    openApiPath: String,
): String =
    """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>${htmlEscape(title)}</title>
      <link rel="stylesheet" href="$SWAGGER_UI_BASE/swagger-ui.css" integrity="$CSS_INTEGRITY" crossorigin="anonymous">
    </head>
    <body>
      <div id="swagger-ui"></div>
      <script src="$SWAGGER_UI_BASE/swagger-ui-bundle.js" integrity="$BUNDLE_INTEGRITY" crossorigin="anonymous"></script>
      <script>
        window.onload = function () {
          window.ui = SwaggerUIBundle({ url: ${scriptString(openApiPath)}, dom_id: "#swagger-ui" });
        };
      </script>
    </body>
    </html>
    """.trimIndent()

private fun htmlEscape(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

/** String literal for a `<script>`: JSON with `<`, `>` and `&` escaped so it cannot close the tag. */
private fun scriptString(text: String): String =
    JsonPrimitive(text)
        .toString()
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
        .replace("&", "\\u0026")
