package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.Transaction
import maryk.foundationdb.subspace.Subspace

import maryk.foundationdb.tuple.Tuple

expect open class DirectorySubspace : Subspace, Directory {
    open override fun getDirectoryLayer(): DirectoryLayer
    open override fun createOrOpen(context: TransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace>
    open override fun open(context: ReadTransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace>
    open override fun create(context: TransactionContext, subpath: List<String>, layer: ByteArray, prefix: ByteArray?): FdbFuture<DirectorySubspace>
    open override fun moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace>
    open override fun move(context: TransactionContext, oldSubpath: List<String>, newSubpath: List<String>): FdbFuture<DirectorySubspace>
    open override fun remove(context: TransactionContext, subpath: List<String>): FdbFuture<Unit>
    open override fun removeIfExists(context: TransactionContext, subpath: List<String>): FdbFuture<Boolean>
    open override fun list(context: ReadTransactionContext, subpath: List<String>): FdbFuture<List<String>>
    open override fun exists(context: ReadTransactionContext, subpath: List<String>): FdbFuture<Boolean>

    override val path: List<String>
    override val layer: ByteArray

    override fun pack(): ByteArray
    override fun pack(tuple: Tuple): ByteArray

    fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
}

expect fun DirectorySubspace.asDirectory(): Directory
