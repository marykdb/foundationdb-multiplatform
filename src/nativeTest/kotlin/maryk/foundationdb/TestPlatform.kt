package maryk.foundationdb

import maryk.foundationdb.directory.Directory
import maryk.foundationdb.directory.DirectoryLayer

internal actual fun DirectoryLayer.testDirectoryOrNull(): Directory? = Directory()
