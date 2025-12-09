package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction

import maryk.foundationdb.tuple.Tuple

expect open class DirectorySubspace {
    val path: List<String>
    val layer: ByteArray

    fun pack(): ByteArray
    fun pack(tuple: Tuple): ByteArray

    fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
}

expect fun DirectorySubspace.asDirectory(): Directory
