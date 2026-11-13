package eu.kasify.model
import eu.kasify.db.model.PropertyNukiSmartlocksTable
import eu.kasify.db.model.PropertySmartlocksTable
import eu.kasify.db.model.PropertyUnifiSmartlocksTable
import eu.kasify.db.model.SmartlockVendor
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

data class PropertySmartlock(
    val id: Uuid,
    val propertyId: Uuid,
    val vendor: SmartlockVendor,
    val name: String,
    val nukiSmartlock: NukiSmartlock? = null,
    val unifiSmartlock: UnifiSmartlock? = null,
    val lockState: Int? = null,
    val batteryLevel: Int? = null,
    val batteryCharging: Boolean? = null,
) {
    companion object {
        fun from(row: ResultRow): PropertySmartlock =
            PropertySmartlock(
                id = row[PropertySmartlocksTable.id],
                propertyId = row[PropertySmartlocksTable.propertyId],
                vendor = row[PropertySmartlocksTable.vendor],
                name = row[PropertySmartlocksTable.name],
                nukiSmartlock =
                    row.getOrNull(PropertyNukiSmartlocksTable.id)?.let {
                        NukiSmartlock(
                            id = it,
                            nukiSmartlockId = row[PropertyNukiSmartlocksTable.nukiSmartlockId],
                            nukiIntegrationId = row[PropertyNukiSmartlocksTable.nukiIntegrationId],
                        )
                    },
                unifiSmartlock =
                    row.getOrNull(PropertyUnifiSmartlocksTable.id)?.let {
                        UnifiSmartlock(
                            id = it,
                            floorId = row[PropertyUnifiSmartlocksTable.floorId],
                            doorId = row[PropertyUnifiSmartlocksTable.doorId],
                        )
                    },
            )
    }
}

data class NukiSmartlock(
    val id: Uuid,
    val nukiSmartlockId: String,
    val nukiIntegrationId: Uuid,
)

data class UnifiSmartlock(
    val id: Uuid,
    val floorId: String,
    val doorId: String,
)
