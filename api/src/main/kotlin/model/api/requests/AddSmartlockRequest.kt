package eu.kasify.model.api.requests

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

const val SMARTLOCK_MAX_NAME_LENGTH = 100

@Serializable
data class AddSmartlockRequest(
    val name: String,
    @SerialName("nuki_smartlock_id")
    val nukiSmartlockId: String? = null,
    @SerialName("nuki_integration_id")
    val nukiIntegrationId: Uuid? = null,
    @SerialName("unifi_floor_id")
    val unifiFloorId: String? = null,
    @SerialName("unifi_door_id")
    val unifiDoorId: String? = null,
)

fun AddSmartlockRequest.validate(): ValidationResult {
    val errors = mutableListOf<String>()

    if (name.isBlank()) {
        errors.add("Smartlock name must not be blank")
    }

    if (name.length > SMARTLOCK_MAX_NAME_LENGTH) {
        errors.add("Smartlock name must not exceed $SMARTLOCK_MAX_NAME_LENGTH characters")
    }

    val hasNuki = !nukiSmartlockId.isNullOrBlank()
    val hasUnifiFloor = !unifiFloorId.isNullOrBlank()
    val hasUnifiDoor = !unifiDoorId.isNullOrBlank()
    val hasUnifi = hasUnifiFloor && hasUnifiDoor

    if (!hasNuki && !hasUnifi) {
        errors.add("Must provide either nuki_smartlock_id or both unifi_floor_id and unifi_door_id")
    }

    if (hasNuki && nukiIntegrationId == null) {
        errors.add("nuki_integration_id is required when nuki_smartlock_id is provided")
    }

    if (hasNuki && (hasUnifiFloor || hasUnifiDoor)) {
        errors.add("Cannot provide both nuki_smartlock_id and unifi fields")
    }

    if ((hasUnifiFloor && !hasUnifiDoor) || (!hasUnifiFloor && hasUnifiDoor)) {
        errors.add("Must provide both unifi_floor_id and unifi_door_id together")
    }

    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
}
