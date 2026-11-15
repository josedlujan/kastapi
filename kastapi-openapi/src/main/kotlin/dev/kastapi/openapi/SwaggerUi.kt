package dev.kastapi.openapi

import kotlinx.serialization.json.JsonPrimitive

private const val SWAGGER_UI_BASE = "https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.34.1"

// Hashes SRI (sha384) de los recursos de la versión fijada; el navegador rechaza un archivo alterado.
private const val CSS_INTEGRITY = "sha384-4c3vsE5f+trhFyHrFrLM1fFGUn531NIaC7dcqEWIJdoRQJ5CkJbSuSCwXLpaS1KF"
private const val BUNDLE_INTEGRITY = "sha384-XFYN47VzPiDY6qPtxYKg0nqUeW/upo20xf1j2SUE7gT/i35FJB4cuS/71Iegzk8Z"

/**
 * HTML de Swagger UI: carga `swagger-ui-dist` desde un CDN con versión fija y apunta a [openApiPath].
 *
 * @param title título de la página.
 * @param openApiPath ruta desde la que se sirve el documento OpenAPI.
 */
@InternalKastApi
public fun swaggerUiHtml(
    title: String,
    openApiPath: String,
): String =
    """
    <!DOCTYPE html>
    <html lang="es">
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

/** Literal de cadena para un `<script>`: JSON con `<`, `>` y `&` escapados para que no pueda cerrar la etiqueta. */
private fun scriptString(text: String): String =
    JsonPrimitive(text)
        .toString()
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
        .replace("&", "\\u0026")
