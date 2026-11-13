package eu.kasify.helpers

fun loadNukiTestResource(fileName: String): String =
    object {}
        .javaClass.classLoader
        .getResourceAsStream("nuki/$fileName")
        ?.readBytes()
        ?.decodeToString()
        ?: throw IllegalArgumentException("Resource not found: nuki/$fileName")

fun loadTestResource(fileName: String): String =
    object {}
        .javaClass.classLoader
        .getResourceAsStream(fileName)
        ?.readBytes()
        ?.decodeToString()
        ?: throw IllegalArgumentException("Resource not found: nuki/$fileName")
