package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.subspace.Subspace

expect class DirectoryLayer {
    fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun create(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray = byteArrayOf(),
        prefix: ByteArray? = null
    ): FdbFuture<DirectorySubspace>
    fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun open(readTransaction: ReadTransaction, path: List<String>): FdbFuture<DirectorySubspace>

    companion object {
        fun getDefault(): DirectoryLayer
        fun from(
            nodeSubspace: Subspace,
            contentSubspace: Subspace = Subspace(),
            allowManualPrefixes: Boolean = false
        ): DirectoryLayer
    }
}
