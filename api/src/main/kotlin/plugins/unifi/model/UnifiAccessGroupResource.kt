package eu.kasify.plugins.unifi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an access group with its resources (doors, floors, buildings, elevators, etc.)
 */
@Serializable
data class UnifiAccessGroupResource(
    @SerialName("group_name")
    val groupName: String,
    @SerialName("group_type")
    val groupType: String,
    @SerialName("group_id")
    val groupId: String,
    @SerialName("group_configs")
    val groupConfigs: String? = null,
    @SerialName("group_resources")
    val groupResources: List<UnifiGroupResourceItem> = emptyList(),
)

/**
 * Represents a resource item within a group (e.g., a floor containing doors)
 */
@Serializable
data class UnifiGroupResourceItem(
    @SerialName("resource_type")
    val resourceType: String,
    @SerialName("resource_id")
    val resourceId: String,
    @SerialName("resource_name")
    val resourceName: String,
    @SerialName("resource_extra_type")
    val resourceExtraType: String? = null,
    @SerialName("resources")
    val resources: List<UnifiResourceItem> = emptyList(),
)

/**
 * Represents a nested resource (e.g., a door within a floor)
 */
@Serializable
data class UnifiResourceItem(
    @SerialName("resource_type")
    val resourceType: String,
    @SerialName("resource_id")
    val resourceId: String,
    @SerialName("resource_name")
    val resourceName: String,
    @SerialName("resource_extra_type")
    val resourceExtraType: String? = null,
    // Not from JSON - populated by getDoors() from parent floor
    @SerialName("floor_id")
    val floorId: String = "",
)
