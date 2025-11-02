package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction

expect class DirectoryLayer {
    fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun open(readTransaction: ReadTransaction, path: List<String>): FdbFuture<DirectorySubspace>

    companion object {
        fun getDefault(): DirectoryLayer
    }
}
