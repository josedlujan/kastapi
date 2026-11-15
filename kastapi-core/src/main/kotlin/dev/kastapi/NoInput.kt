package dev.kastapi

import kotlinx.serialization.Serializable

/**
 * Input model for routes that receive no data.
 *
 * It is used as the `I` type in the DSL: `get<NoInput, Health>("/health") { Health("ok") }`.
 */
@Serializable
public object NoInput
