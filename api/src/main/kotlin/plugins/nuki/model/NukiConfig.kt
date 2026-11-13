package eu.kasify.plugins.nuki.model

import kotlinx.serialization.Serializable

@Serializable
data class NukiConfig(
    val name: String,
    val keypadPaired: Boolean? = null,
    val keypad2Paired: Boolean? = null,
)
