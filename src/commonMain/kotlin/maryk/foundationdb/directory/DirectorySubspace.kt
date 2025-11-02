package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction

import maryk.foundationdb.tuple.Tuple

expect open class DirectorySubspace {
    fun pack(): ByteArray
    fun pack(tuple: Tuple): ByteArray

    fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
}
