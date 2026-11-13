package eu.kasify.model
import eu.kasify.db.model.FileType
import eu.kasify.db.model.FilesTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

data class FileRecord(
    val id: Uuid,
    val accountId: Uuid,
    val propertyId: Uuid?,
    val fileType: FileType,
    val originalFilename: String,
    val contentType: String,
    val sizeBytes: Long,
    val storageKey: String,
    val url: String,
    val uploadedBy: Uuid,
) {
    companion object : MapModel<FileRecord> {
        override fun from(row: ResultRow): FileRecord =
            FileRecord(
                id = row[FilesTable.id],
                accountId = row[FilesTable.accountId],
                propertyId = row[FilesTable.propertyId],
                fileType = row[FilesTable.fileType],
                originalFilename = row[FilesTable.originalFilename],
                contentType = row[FilesTable.contentType],
                sizeBytes = row[FilesTable.sizeBytes],
                storageKey = row[FilesTable.storageKey],
                url = row[FilesTable.url],
                uploadedBy = row[FilesTable.uploadedBy],
            )
    }
}
