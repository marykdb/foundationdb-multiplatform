package maryk.foundationdb.platform

import java.nio.file.Files

actual fun createTempDirectory(prefix: String): String =
    Files.createTempDirectory(prefix).toFile().apply { deleteOnExit() }.absolutePath
