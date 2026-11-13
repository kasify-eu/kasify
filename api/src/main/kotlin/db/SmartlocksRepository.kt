package eu.kasify.db
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyNukiSmartlocksTable
import eu.kasify.db.model.PropertySmartlocksTable
import eu.kasify.db.model.PropertyUnifiSmartlocksTable
import eu.kasify.db.model.SmartlockVendor
import eu.kasify.model.PropertySmartlock
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

data class NukiSmartlockAssignment(
    val propertyId: Uuid,
    val propertyName: String,
    val smartlockRecordId: Uuid,
    val nukiSmartlockId: String,
    val name: String,
)

data class UnifiSmartlockAssignment(
    val propertyId: Uuid,
    val propertyName: String,
    val smartlockRecordId: Uuid,
    val floorId: String,
    val doorId: String,
)

class SmartlocksRepository {
    /**
     * Distinct smart-lock vendors configured per property, batched for a page of
     * properties (admin list enrichment — avoids an N+1 lookup per row).
     */
    suspend fun getVendorsByPropertyIds(propertyIds: List<Uuid>): Map<Uuid, Set<SmartlockVendor>> =
        dbQuery {
            if (propertyIds.isEmpty()) {
                emptyMap()
            } else {
                PropertySmartlocksTable
                    .select(PropertySmartlocksTable.propertyId, PropertySmartlocksTable.vendor)
                    .where { PropertySmartlocksTable.propertyId inList propertyIds }
                    .groupBy({ it[PropertySmartlocksTable.propertyId] }, { it[PropertySmartlocksTable.vendor] })
                    .mapValues { (_, vendors) -> vendors.toSet() }
            }
        }

    suspend fun getSmartlocksByProperty(propertyId: Uuid): List<PropertySmartlock> =
        dbQuery {
            PropertySmartlocksTable
                .join(PropertiesTable, JoinType.INNER, PropertySmartlocksTable.propertyId, PropertiesTable.id)
                .join(
                    PropertyNukiSmartlocksTable,
                    JoinType.LEFT,
                    PropertySmartlocksTable.id,
                    PropertyNukiSmartlocksTable.propertySmartlockId,
                ).join(
                    PropertyUnifiSmartlocksTable,
                    JoinType.LEFT,
                    PropertySmartlocksTable.id,
                    PropertyUnifiSmartlocksTable.propertySmartlockId,
                ).selectAll()
                .where {
                    (PropertySmartlocksTable.propertyId eq propertyId) and
                        (PropertiesTable.deletedAt.isNull())
                }.map { PropertySmartlock.Companion.from(it) }
        }

    suspend fun getSmartlockById(smartlockId: Uuid): PropertySmartlock? =
        dbQuery {
            PropertySmartlocksTable
                .join(PropertiesTable, JoinType.INNER, PropertySmartlocksTable.propertyId, PropertiesTable.id)
                .join(
                    PropertyNukiSmartlocksTable,
                    JoinType.LEFT,
                    PropertySmartlocksTable.id,
                    PropertyNukiSmartlocksTable.propertySmartlockId,
                ).join(
                    PropertyUnifiSmartlocksTable,
                    JoinType.LEFT,
                    PropertySmartlocksTable.id,
                    PropertyUnifiSmartlocksTable.propertySmartlockId,
                ).selectAll()
                .where {
                    (PropertySmartlocksTable.id eq smartlockId) and
                        (PropertiesTable.deletedAt.isNull())
                }.map { PropertySmartlock.from(it) }
                .singleOrNull()
        }

    suspend fun nukiSmartlockIdExists(
        propertyId: Uuid,
        nukiSmartlockId: String,
    ): Boolean =
        dbQuery {
            PropertySmartlocksTable
                .join(PropertyNukiSmartlocksTable, JoinType.INNER, PropertySmartlocksTable.id, PropertyNukiSmartlocksTable.propertySmartlockId)
                .selectAll()
                .where {
                    (PropertySmartlocksTable.propertyId eq propertyId) and
                        (PropertyNukiSmartlocksTable.nukiSmartlockId eq nukiSmartlockId)
                }.count() > 0
        }

    suspend fun createUnifiSmartlock(
        propertyId: Uuid,
        name: String,
        floorId: String,
        doorId: String,
    ): Uuid =
        dbQuery {
            // First, create the generic smartlock entry
            val smartlockId =
                PropertySmartlocksTable.insert {
                    it[PropertySmartlocksTable.propertyId] = propertyId
                    it[PropertySmartlocksTable.vendor] = SmartlockVendor.Unifi
                    it[PropertySmartlocksTable.name] = name
                }[PropertySmartlocksTable.id]

            // Then, create the Unifi-specific smartlock entry
            PropertyUnifiSmartlocksTable.insert {
                it[PropertyUnifiSmartlocksTable.propertySmartlockId] = smartlockId
                it[PropertyUnifiSmartlocksTable.floorId] = floorId
                it[PropertyUnifiSmartlocksTable.doorId] = doorId
            }

            smartlockId
        }

    suspend fun createNukiSmartlock(
        propertyId: Uuid,
        name: String,
        nukiSmartlockId: String,
        nukiIntegrationId: Uuid,
    ): Uuid =
        dbQuery {
            // First, create the generic smartlock entry
            val smartlockId =
                PropertySmartlocksTable.insert {
                    it[PropertySmartlocksTable.propertyId] = propertyId
                    it[PropertySmartlocksTable.vendor] = SmartlockVendor.Nuki
                    it[PropertySmartlocksTable.name] = name
                }[PropertySmartlocksTable.id]

            // Then, create the Nuki-specific smartlock entry
            PropertyNukiSmartlocksTable.insert {
                it[PropertyNukiSmartlocksTable.propertySmartlockId] = smartlockId
                it[PropertyNukiSmartlocksTable.nukiIntegrationId] = nukiIntegrationId
                it[PropertyNukiSmartlocksTable.nukiSmartlockId] = nukiSmartlockId
            }

            smartlockId
        }

    suspend fun deleteSmartlock(smartlockId: Uuid): Boolean =
        dbQuery {
            val deleted =
                PropertySmartlocksTable.deleteWhere {
                    PropertySmartlocksTable.id eq smartlockId
                }
            deleted > 0
        }

    suspend fun getNukiSmartlocksByAccount(accountId: Uuid): List<NukiSmartlockAssignment> =
        dbQuery {
            PropertySmartlocksTable
                .join(PropertiesTable, JoinType.INNER, PropertySmartlocksTable.propertyId, PropertiesTable.id)
                .join(
                    PropertyNukiSmartlocksTable,
                    JoinType.INNER,
                    PropertySmartlocksTable.id,
                    PropertyNukiSmartlocksTable.propertySmartlockId,
                ).selectAll()
                .where {
                    (PropertiesTable.accountId eq accountId) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (PropertySmartlocksTable.vendor eq SmartlockVendor.Nuki)
                }.map {
                    NukiSmartlockAssignment(
                        propertyId = it[PropertiesTable.id],
                        propertyName = it[PropertiesTable.name],
                        smartlockRecordId = it[PropertySmartlocksTable.id],
                        nukiSmartlockId = it[PropertyNukiSmartlocksTable.nukiSmartlockId],
                        name = it[PropertySmartlocksTable.name],
                    )
                }
        }

    suspend fun getUnifiSmartlocksByAccount(accountId: Uuid): List<UnifiSmartlockAssignment> =
        dbQuery {
            PropertySmartlocksTable
                .join(PropertiesTable, JoinType.INNER, PropertySmartlocksTable.propertyId, PropertiesTable.id)
                .join(
                    PropertyUnifiSmartlocksTable,
                    JoinType.INNER,
                    PropertySmartlocksTable.id,
                    PropertyUnifiSmartlocksTable.propertySmartlockId,
                ).selectAll()
                .where {
                    (PropertiesTable.accountId eq accountId) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (PropertySmartlocksTable.vendor eq SmartlockVendor.Unifi)
                }.map {
                    UnifiSmartlockAssignment(
                        propertyId = it[PropertiesTable.id],
                        propertyName = it[PropertiesTable.name],
                        smartlockRecordId = it[PropertySmartlocksTable.id],
                        floorId = it[PropertyUnifiSmartlocksTable.floorId],
                        doorId = it[PropertyUnifiSmartlocksTable.doorId],
                    )
                }
        }

    suspend fun isNukiSmartlockAssignedToAnyProperty(
        accountId: Uuid,
        nukiSmartlockId: String,
    ): Boolean =
        dbQuery {
            PropertySmartlocksTable
                .join(PropertiesTable, JoinType.INNER, PropertySmartlocksTable.propertyId, PropertiesTable.id)
                .join(
                    PropertyNukiSmartlocksTable,
                    JoinType.INNER,
                    PropertySmartlocksTable.id,
                    PropertyNukiSmartlocksTable.propertySmartlockId,
                ).selectAll()
                .where {
                    (PropertiesTable.accountId eq accountId) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (PropertyNukiSmartlocksTable.nukiSmartlockId eq nukiSmartlockId)
                }.count() > 0
        }

    suspend fun isUnifiSmartlockAssignedToAnyProperty(
        accountId: Uuid,
        doorId: String,
    ): Boolean =
        dbQuery {
            PropertySmartlocksTable
                .join(PropertiesTable, JoinType.INNER, PropertySmartlocksTable.propertyId, PropertiesTable.id)
                .join(
                    PropertyUnifiSmartlocksTable,
                    JoinType.INNER,
                    PropertySmartlocksTable.id,
                    PropertyUnifiSmartlocksTable.propertySmartlockId,
                ).selectAll()
                .where {
                    (PropertiesTable.accountId eq accountId) and
                        (PropertiesTable.deletedAt.isNull()) and
                        (PropertyUnifiSmartlocksTable.doorId eq doorId)
                }.count() > 0
        }
}
