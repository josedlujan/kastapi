# Contribuir a KastAPI

¡Gracias por tu interés!

## Antes de empezar

- Para cambios grandes o en la API pública, abre primero un issue o un ADR (`docs/adr/TEMPLATE.md`).
- Busca issues con `good first issue` si es tu primera contribución.

## Entorno

- JDK 21
- `./gradlew build` debe pasar antes de abrir un PR.

## Estilo

- Kotlin oficial + ktlint (`./gradlew ktlintFormat`).
- `explicitApi()`: declara visibilidad explícita.
- KDoc en todo lo público.
- Conventional Commits.

## Pull requests

- Una cosa por PR.
- Incluye pruebas.
- Actualiza `CHANGELOG.md` en `Unreleased`.
- Si cambias API pública, ejecuta `./gradlew apiDump` y explica el cambio.

## Comandos útiles

```bash
./gradlew build                  # compila, pruebas y checks
./gradlew ktlintFormat           # corrige el estilo
./gradlew apiCheck               # compatibilidad de la API pública
./gradlew publishToMavenLocal    # instala los módulos para probarlos en otro proyecto
./gradlew :examples:hello:run    # ejemplo en http://localhost:8080
```
