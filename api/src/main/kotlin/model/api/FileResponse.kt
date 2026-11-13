package eu.kasify.model.api

import eu.kasify.model.FileRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class FileResponse(
    val id: Uuid,
    val url: String,
    @SerialName("original_filename")
    val originalFilename: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
) {
    companion object {
        fun from(record: FileRecord): FileResponse =
            FileResponse(
                id = record.id,
                url = record.url,
                originalFilename = record.originalFilename,
                contentType = record.contentType,
                sizeBytes = record.sizeBytes,
            )
    }
}
