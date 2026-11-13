package eu.kasify.services

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging

class R2StorageService(
    private val bucket: String,
    private val endpoint: String,
    private val publicBaseUrl: String,
    private val accessKeyId: String,
    private val secretAccessKey: String,
) {
    private val logger = KotlinLogging.logger {}

    private val client: S3Client by lazy {
        S3Client {
            region = "auto"
            endpointUrl = Url.parse(endpoint)
            credentialsProvider =
                StaticCredentialsProvider {
                    this.accessKeyId = this@R2StorageService.accessKeyId
                    this.secretAccessKey = this@R2StorageService.secretAccessKey
                }
        }
    }

    suspend fun upload(
        key: String,
        content: ByteArray,
        contentType: String,
    ): String {
        logger.info { "Uploading file to R2: $key (${content.size} bytes, $contentType)" }

        client.putObject(
            PutObjectRequest {
                this.bucket = this@R2StorageService.bucket
                this.key = key
                this.contentType = contentType
                this.body = ByteStream.fromBytes(content)
            },
        )

        return getPublicUrl(key)
    }

    suspend fun delete(key: String) {
        logger.info { "Deleting file from R2: $key" }

        client.deleteObject(
            DeleteObjectRequest {
                this.bucket = this@R2StorageService.bucket
                this.key = key
            },
        )
    }

    fun getPublicUrl(key: String): String = "${publicBaseUrl.trimEnd('/')}/$key"
}
