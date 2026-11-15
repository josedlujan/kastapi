# Estado del proyecto

Última actualización: versión 0.1.0.

## Por qué es 0.1.0 y no 1.0

El número sigue el [Versionado Semántico](https://semver.org/lang/es/): una versión `0.x` indica desarrollo inicial, y la API pública puede cambiar entre versiones menores. `1.0.0` será el compromiso de mantener la API estable, y todavía no se dan las condiciones:

- Todavía no hay feedback de uso externo.
- No está publicada en Maven Central (hoy se instala con `./gradlew publishToMavenLocal`).
- Quedan limitaciones conocidas (ver más abajo).
- Faltan funciones previstas en el [roadmap](ROADMAP.md): inyección de dependencias, autenticación, generación con KSP y cliente multiplataforma.

## Qué incluye 0.1.0

Descrito en la [referencia](reference.md): DSL tipado sobre Ktor, lectura de path, query y body desde una `data class`, validación con anotaciones, errores con formato único, comprobación de las rutas al arrancar, OpenAPI 3.1, Swagger UI y el módulo `kastapi-test`. El detalle de cada cambio está en el [CHANGELOG](../CHANGELOG.md).

## Limitaciones conocidas

Ordenadas de más a menos importante.

| Limitación | Efecto | Qué haría falta |
| --- | --- | --- |
| Swagger UI está atado a la versión 5.34.1 del CDN | Se verificó en Chrome (la interfaz carga y muestra las operaciones del ejemplo), pero si se actualiza `swagger-ui-dist` hay que recalcular los hashes SRI | Una prueba automática en navegador no está prevista |
| `@Pattern` no protege contra backtracking catastrófico | Un regex mal escrito puede bloquear un hilo ante una entrada hostil | Documentarlo (hecho en la guía) o añadir un límite de tiempo |
| No se exige `Content-Type: application/json` | Un cuerpo con otro tipo de contenido se interpreta igualmente como JSON | Decidir si responder `415` o seguir permisivo |
| La interfaz de `/docs` depende de un CDN | Sin conexión a internet no se muestra; `/openapi.json` sí se sirve | Servir los recursos desde la propia aplicación (cambia la política de dependencias) |
| `kastapi-core` no incluye logging (SLF4J) | Sin una implementación en el classpath, el log del error `500` no se escribe | Está documentado; el ejemplo usa logback |
| Solo se documenta la respuesta `422` en OpenAPI | No aparecen `400`, `404`, `405`, `413` ni `500` | Respuestas múltiples documentadas (prevista para 0.2) |
| `apiCheck` no cubre las funciones `inline reified` del DSL | Un cambio en `get`, `post`... no lo detecta la herramienta | La compilación del ejemplo y de las pruebas lo cubre en parte |
| El `405` no incluye el header `Allow` | Menos informativo para el cliente | Calcular los métodos permitidos por ruta |
| Un `StatusPages` propio debe instalarse antes de `installKastApi` | Instalarlo después lanza `DuplicatePluginException` | Está documentado |
| Los tipos `sealed`, polimórficos y contextuales se documentan como `{}` | Esquema abierto en OpenAPI | Soporte de `oneOf` y discriminadores |
| Los valores por defecto, `Set` y claves de `Map` no tienen soporte completo en el esquema | Detalles menores del documento | Ampliar el generador de esquemas |
| Los mensajes de error están solo en inglés | Sin internacionalización | Prevista para 0.2 |
| La profundidad máxima del JSON del cuerpo no se ha verificado | Posible consumo de pila con anidamiento extremo | Prueba con entradas muy anidadas y, si hace falta, un límite |
| Las clases compartidas entre módulos son públicas con `@InternalKastApi` | Son públicas en el bytecode, aunque no forman parte de la API estable (`apiCheck` las excluye) | Revisarlo antes de 1.0 |

## Qué falta

### Para cerrar la release 0.1.0

- [x] Código, pruebas, documentación pública y guía.
- [x] Prueba manual de Swagger UI en un navegador (Chrome).
- [x] Tag `v0.1.0`.

### Para 0.2

Condición de entrada del roadmap: 0.1 publicada y primeros usuarios. Contenido previsto: dependencias inyectables, headers y cookies tipados, respuestas múltiples documentadas e internacionalización de los mensajes.

### Para 1.0

Autenticación integrada con OpenAPI, congelar la API, publicar en Maven Central con firma y javadoc, y un sitio de documentación. Condición: feedback de adopción. Antes de congelar la API conviene resolver la visibilidad de `InternalKastApi` y las decisiones abiertas de la tabla anterior.

## Cómo se decide

- Los cambios en la API pública se discuten en un issue y se reflejan en la [referencia](reference.md).
- Las decisiones de diseño con alternativas se registran como ADR en [`docs/adr`](adr).
- Para sugerir un cambio, abre un issue con la plantilla de propuesta (ver [CONTRIBUTING](../CONTRIBUTING.md)).
