package dev.kastapi

/** Data of the documentation's `info` block, shared by the routes block and its groups. */
internal class ApiMetadata {
    var title: String = DEFAULT_TITLE
    var version: String = DEFAULT_VERSION
    var description: String? = null

    companion object {
        const val DEFAULT_TITLE = "KastAPI"
        const val DEFAULT_VERSION = "0.0.0"
    }
}
