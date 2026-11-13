package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object FilesTable : Table(name = "files") {
    val id = uuid("id").autoGenerate()
    val accountId = reference("account_id", AccountsTable.id)
    val propertyId = reference("property_id", PropertiesTable.id).nullable()

    val fileType =
        customEnumeration(
            name = "file_type",
            sql = "file_type",
            fromDb = { value ->
                FileType.entries.find { it.name.equals(value as String, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Invalid FileType: $value")
            },
            toDb = { PGEnum("file_type", it) },
        )

    val originalFilename = varchar("original_filename", length = 255)
    val contentType = varchar("content_type", length = 255)
    val sizeBytes = long("size_bytes")
    val storageKey = text("storage_key").uniqueIndex()
    val url = text("url")
    val uploadedBy = reference("uploaded_by", UsersTable.id)

    val createdAt = timestampWithTimeZone("created_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
