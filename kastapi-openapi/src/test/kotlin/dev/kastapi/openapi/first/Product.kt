package dev.kastapi.openapi.first

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val name: String,
)
