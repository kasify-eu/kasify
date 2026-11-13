package eu.kasify.model.email

data class EmailMessage(
    val to: String,
    val from: String,
    val subject: String,
    val html: String,
    val text: String,
    val tags: Map<String, String> = emptyMap(),
)
