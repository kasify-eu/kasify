package eu.kasify.db
import eu.kasify.db.model.FileType
import eu.kasify.db.model.FilesTable
import eu.kasify.model.FileRecord
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

class FilesRepository {
    suspend fun create(
        accountId: Uuid,
        propertyId: Uuid?,
        fileType: FileType,
        originalFilename: String,
        contentType: String,
        sizeBytes: Long,
        storageKey: String,
        url: String,
        uploadedBy: Uuid,
    ): FileRecord =
        dbQuery {
            val id =
                FilesTable.insert {
                    it[FilesTable.accountId] = accountId
                    if (propertyId != null) {
                        it[FilesTable.propertyId] = propertyId
                    }
                    it[FilesTable.fileType] = fileType
                    it[FilesTable.originalFilename] = originalFilename
                    it[FilesTable.contentType] = contentType
                    it[FilesTable.sizeBytes] = sizeBytes
                    it[FilesTable.storageKey] = storageKey
                    it[FilesTable.url] = url
                    it[FilesTable.uploadedBy] = uploadedBy
                }[FilesTable.id]

            FileRecord(
                id = id,
                accountId = accountId,
                propertyId = propertyId,
                fileType = fileType,
                originalFilename = originalFilename,
                contentType = contentType,
                sizeBytes = sizeBytes,
                storageKey = storageKey,
                url = url,
                uploadedBy = uploadedBy,
            )
        }

    suspend fun getByPropertyId(
        propertyId: Uuid,
        fileType: FileType? = null,
    ): List<FileRecord> =
        dbQuery {
            FilesTable
                .selectAll()
                .where {
                    val base = (FilesTable.propertyId eq propertyId) and (FilesTable.deletedAt.isNull())
                    if (fileType != null) {
                        base and (FilesTable.fileType eq fileType)
                    } else {
                        base
                    }
                }.map { FileRecord.from(it) }
        }

    suspend fun getById(id: Uuid): FileRecord? =
        dbQuery {
            FilesTable
                .selectAll()
                .where { (FilesTable.id eq id) and (FilesTable.deletedAt.isNull()) }
                .singleOrNull()
                ?.let { FileRecord.from(it) }
        }

    suspend fun softDelete(id: Uuid): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(TimeZone.of("Europe/Madrid").toJavaZoneId()).toOffsetDateTime()

            FilesTable.update({ FilesTable.id eq id }) {
                it[deletedAt] = currentDateTime
            } > 0
        }
}
