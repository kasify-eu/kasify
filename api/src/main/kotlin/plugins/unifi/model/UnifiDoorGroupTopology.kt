package eu.kasify.plugins.unifi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnifiDoorGroupTopology(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("resource_topologies")
    val resourceTopologies: List<UnifiResourceTopology>,
)

@Serializable
data class UnifiResourceTopology(
    val id: String,
    val name: String,
    val type: String,
    val resources: List<UnifiDoorResource>,
)

@Serializable
data class UnifiDoorResource(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("is_bind_hub")
    val isBindHub: Boolean,
)
