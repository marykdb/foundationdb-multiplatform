package maryk.foundationdb

import maryk.foundationdb.directory.Directory
import maryk.foundationdb.directory.DirectoryLayer

internal expect fun DirectoryLayer.testDirectoryOrNull(): Directory?
