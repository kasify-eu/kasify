package eu.kasify.model
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyAccessTable
import eu.kasify.db.model.PropertyService
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

data class PropertyAccess(
    val id: Uuid,
    val userId: Uuid,
    val propertyId: Uuid,
    val service: PropertyService?,
    val accessLevel: PropertyAccessLevel,
) {
    companion object : MapModel<PropertyAccess> {
        override fun from(row: ResultRow): PropertyAccess =
            PropertyAccess(
                id = row[PropertyAccessTable.id],
                userId = row[PropertyAccessTable.userId],
                propertyId = row[PropertyAccessTable.propertyId],
                service = row[PropertyAccessTable.service],
                accessLevel = row[PropertyAccessTable.accessLevel],
            )
    }
}
