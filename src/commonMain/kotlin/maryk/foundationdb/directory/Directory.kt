package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.TransactionContext

expect class Directory {
    val path: List<String>
    val layer: ByteArray

    fun createOrOpen(context: TransactionContext, subpath: List<String>, layer: ByteArray = byteArrayOf()): FdbFuture<DirectorySubspace>
    fun open(context: ReadTransactionContext, subpath: List<String>, layer: ByteArray = byteArrayOf()): FdbFuture<DirectorySubspace>
    fun create(context: TransactionContext, subpath: List<String>, layer: ByteArray = byteArrayOf(), prefix: ByteArray? = null): FdbFuture<DirectorySubspace>
    fun moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace>
    fun move(context: TransactionContext, oldSubpath: List<String>, newSubpath: List<String>): FdbFuture<DirectorySubspace>
    fun remove(context: TransactionContext, subpath: List<String> = emptyList()): FdbFuture<Unit>
    fun removeIfExists(context: TransactionContext, subpath: List<String> = emptyList()): FdbFuture<Boolean>
    fun list(context: ReadTransactionContext, subpath: List<String> = emptyList()): FdbFuture<List<String>>
    fun exists(context: ReadTransactionContext, subpath: List<String> = emptyList()): FdbFuture<Boolean>
}

typealias DirectoryPartition = DirectorySubspace
