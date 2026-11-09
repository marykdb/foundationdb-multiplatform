package maryk.foundationdb.directory

open class DirectoryException(
    message: String,
    val path: List<String>
) : RuntimeException("$message: path=${DirectoryUtil.pathStr(path)}")

class DirectoryAlreadyExistsException(path: List<String>) :
    DirectoryException("Directory already exists", path)

class NoSuchDirectoryException(path: List<String>) :
    DirectoryException("No such directory", path)

class MismatchedLayerException(
    path: List<String>,
    val stored: ByteArray,
    val opened: ByteArray
) : DirectoryException(
    "Mismatched layer: stored=${stored.toHexString()}, opened=${opened.toHexString()}",
    path
)

class DirectoryMoveException(
    val sourcePath: List<String>,
    val destPath: List<String>,
    message: String = "Invalid directory move"
) : RuntimeException(
    "$message: sourcePath=${DirectoryUtil.pathStr(sourcePath)}, destPath=${DirectoryUtil.pathStr(destPath)}"
)

open class DirectoryVersionException(message: String) : RuntimeException(message)

/** Canonical Native access found recognizable legacy partition metadata. */
class DirectoryMigrationRequiredException(
    path: List<String>,
    val detectedLayout: DirectoryLayout,
) : DirectoryVersionException(
    "Legacy Native partition metadata detected at ${DirectoryUtil.pathStr(path)} ($detectedLayout); " +
        "stop writers and run an offline DirectoryTreeMigration before canonical access",
) {
    private val storedPath = path.toList()
    val path: List<String> get() = storedPath.toList()
}
