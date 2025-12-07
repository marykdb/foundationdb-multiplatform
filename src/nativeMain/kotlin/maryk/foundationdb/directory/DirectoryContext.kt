package maryk.foundationdb.directory

import maryk.foundationdb.subspace.Subspace

internal data class DirectoryContext(
    val nodeSubspace: Subspace,
    val contentSubspace: Subspace,
    val allowManualPrefixes: Boolean = false
) {
    companion object {
        fun default(): DirectoryContext = DirectoryContext(
            nodeSubspace = Subspace(byteArrayOf(0xFE.toByte())),
            contentSubspace = Subspace(),
            allowManualPrefixes = false
        )
    }
}
