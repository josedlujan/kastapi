# Política de seguridad

## Versiones con soporte

KastAPI está en la versión 0.1.0, experimental. Solo se corrigen vulnerabilidades en la última versión publicada.

## Cómo reportar una vulnerabilidad

No abras un issue público. Usa el reporte privado de GitHub: pestaña **Security** del repositorio, **Report a vulnerability**. Si no está disponible, escribe a josedlujan@hotmail.com con el asunto "KastAPI: seguridad".

Incluye, si puedes:

- Versión de KastAPI, de Kotlin y de Ktor.
- Código mínimo para reproducirlo.
- Qué esperabas y qué ocurrió.

Responderé en un plazo razonable y te avisaré cuando haya una corrección publicada.

## Qué cuenta como vulnerabilidad

Por ejemplo: respuestas de error que filtren información interna, entradas que consuman recursos de forma desproporcionada (como expresiones regulares o cuerpos sin límite) o formas de saltarse la validación declarada.

Las limitaciones conocidas están en [docs/STATUS.md](docs/STATUS.md). En particular, `@Pattern` usa la expresión regular que escribe quien desarrolla: un patrón con backtracking catastrófico es responsabilidad de esa persona.
