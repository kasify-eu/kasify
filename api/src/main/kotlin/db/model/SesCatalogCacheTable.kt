package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object SesCatalogCacheTable : Table(name = "ses_catalog_cache") {
    val id = uuid("id").autoGenerate()
    val catalogType = varchar("catalog_type", 50)
    val code = varchar("code", 20)
    val description = text("description")
    val fetchedAt = timestampWithTimeZone("fetched_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(catalogType, code)
    }
}
