package maryk.foundationdb.directory

import maryk.foundationdb.subspace.Subspace

internal data class DirectoryContext(
    val nodeSubspace: Subspace,
    val contentSubspace: Subspace,
    val allowManualPrefixes: Boolean = false,
    val layout: DirectoryLayout = DirectoryLayout.JAVA_CANONICAL,
    val pathPrefix: List<String> = emptyList()
) {
    fun partition(prefix: ByteArray, path: List<String>): DirectoryContext = if (layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) this else if (layout == DirectoryLayout.JAVA_CANONICAL) {
        DirectoryContext(Subspace(prefix + byteArrayOf(0xfe.toByte())), Subspace(prefix), false, layout, pathPrefix + path)
    } else {
        DirectoryContext(nodeSubspace.get(prefix), contentSubspace.get(prefix), allowManualPrefixes, layout, pathPrefix + path)
    }

    companion object {
        fun default(): DirectoryContext = DirectoryContext(
            nodeSubspace = Subspace(byteArrayOf(0xFE.toByte())),
            contentSubspace = Subspace(),
            allowManualPrefixes = false
        )
    }
}
