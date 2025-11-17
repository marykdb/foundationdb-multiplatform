package maryk.foundationdb.platform

/** Creates a new temporary directory and returns its absolute path. */
expect fun createTempDirectory(prefix: String): String
