# ADR-0001: Construir sobre Ktor

- Estado: Aceptado
- Fecha: 2026-11-10

## Contexto

Un framework HTTP nuevo exige resolver servidor, engines, plugins, TLS, HTTP/2, testing. Ese no es el valor diferencial del proyecto.

## Decisión

KastAPI es una capa sobre Ktor. Usa su servidor (Netty por defecto), routing, content negotiation y status pages. Expone `Application.installKastApi` para integrarse en apps Ktor existentes y `KastCall.call` como escape.

## Consecuencias

- Ganamos ecosistema (plugins de CORS, auth, métricas) desde el día uno.
- Dependemos de la evolución de la API de Ktor; fijamos versión en el catálogo y probamos en CI.
- El usuario puede bajar a Ktor puro en cualquier ruta.
