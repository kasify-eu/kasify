package eu.kasify.plugins.nuki

import eu.kasify.plugins.PluginException

open class NukiException : PluginException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
