package maryk.foundationdb.directory

internal actual fun DirectorySubspace.testRawPrefix(): ByteArray = this.pack()
