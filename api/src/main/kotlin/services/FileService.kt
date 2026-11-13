package eu.kasify.services
import eu.kasify.db.FilesRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.model.FileType
import eu.kasify.model.FileRecord
import eu.kasify.model.Property
import eu.kasify.security.KasifyUserPrincipal
import io.github.oshai.kotlinlogging.KotlinLogging

class FileService(
    private val filesRepository: FilesRepository,
    private val propertiesRepository: PropertiesRepository,
    private val r2StorageService: R2StorageService?,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val MAX_IMAGE_SIZE = 5L * 1024 * 1024 // 5MB
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }

    suspend fun uploadPropertyCoverImage(
        user: KasifyUserPrincipal,
        property: Property,
        fileBytes: ByteArray,
        contentType: String,
        originalFilename: String,
    ): FileRecord {
        val storage = r2StorageService ?: error("File storage is not configured")

        validateImageFile(fileBytes, contentType)

        // Soft-delete existing cover image if present
        val existingFiles = filesRepository.getByPropertyId(property.id, FileType.cover_image)
        for (existing in existingFiles) {
            try {
                storage.delete(existing.storageKey)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to delete old cover image from R2: ${existing.storageKey}" }
            }
            filesRepository.softDelete(existing.id)
        }

        // Generate storage key
        val extension = extensionFromContentType(contentType)
        val timestamp = System.currentTimeMillis()
        val storageKey = "${property.accountId}/properties/${property.id}/cover-$timestamp.$extension"

        // Upload to R2
        val url = storage.upload(storageKey, fileBytes, contentType)

        // Insert file record
        val fileRecord =
            filesRepository.create(
                accountId = property.accountId,
                propertyId = property.id,
                fileType = FileType.cover_image,
                originalFilename = originalFilename,
                contentType = contentType,
                sizeBytes = fileBytes.size.toLong(),
                storageKey = storageKey,
                url = url,
                uploadedBy = user.userId,
            )

        // Update denormalized column
        propertiesRepository.updateCoverImageUrl(property.id, url)

        logger.info { "Uploaded cover image for property ${property.id}: $url" }

        return fileRecord
    }

    suspend fun deletePropertyCoverImage(property: Property) {
        val storage = r2StorageService ?: error("File storage is not configured")

        val existingFiles = filesRepository.getByPropertyId(property.id, FileType.cover_image)
        for (existing in existingFiles) {
            try {
                storage.delete(existing.storageKey)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to delete cover image from R2: ${existing.storageKey}" }
            }
            filesRepository.softDelete(existing.id)
        }

        propertiesRepository.updateCoverImageUrl(property.id, null)

        logger.info { "Deleted cover image for property ${property.id}" }
    }

    private fun validateImageFile(
        fileBytes: ByteArray,
        contentType: String,
    ) {
        if (contentType !in ALLOWED_IMAGE_TYPES) {
            throw IllegalArgumentException("Unsupported image type: $contentType. Allowed: ${ALLOWED_IMAGE_TYPES.joinToString()}")
        }
        if (fileBytes.size > MAX_IMAGE_SIZE) {
            throw IllegalArgumentException("File too large: ${fileBytes.size} bytes. Maximum: $MAX_IMAGE_SIZE bytes (5MB)")
        }
    }

    private fun extensionFromContentType(contentType: String): String =
        when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "bin"
        }
}
