package maryk.foundationdb.directory

/**
 * Test-only helper to obtain the raw prefix for a directory subspace
 * even when the delegate forbids pack() on partition roots.
 */
internal expect fun DirectorySubspace.testRawPrefix(): ByteArray
