package dev.kastapi

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada para rutas que no reciben datos.
 *
 * Se usa como tipo `I` en el DSL: `get<NoInput, Health>("/health") { Health("ok") }`.
 */
@Serializable
public object NoInput
