package eu.kasify.model.api

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class NukiIntegration(
    var id: Uuid? = null,
) {
    companion object {
        fun from(data: eu.kasify.model.NukiIntegration): NukiIntegration =
            NukiIntegration(
                id = data.id,
            )
    }
}
