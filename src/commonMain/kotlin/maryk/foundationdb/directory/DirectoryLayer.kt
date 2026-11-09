package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.Transaction
import maryk.foundationdb.subspace.Subspace

expect class DirectoryLayer : Directory {
    constructor()
    constructor(allowManualPrefixes: Boolean)
    constructor(nodeSubspace: Subspace, contentSubspace: Subspace)
    constructor(nodeSubspace: Subspace, contentSubspace: Subspace = Subspace(), allowManualPrefixes: Boolean = false)
    fun createOrOpen(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun create(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray = byteArrayOf(),
        prefix: ByteArray? = null
    ): FdbFuture<DirectorySubspace>
    fun open(transaction: Transaction, path: List<String>): FdbFuture<DirectorySubspace>
    fun open(readTransaction: ReadTransaction, path: List<String>): FdbFuture<DirectorySubspace>

    override fun createOrOpen(context: TransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace>
    override fun open(context: ReadTransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace>
    override fun create(context: TransactionContext, subpath: List<String>, layer: ByteArray, prefix: ByteArray?): FdbFuture<DirectorySubspace>
    override fun moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace>
    override fun move(context: TransactionContext, oldSubpath: List<String>, newSubpath: List<String>): FdbFuture<DirectorySubspace>
    override fun remove(context: TransactionContext, subpath: List<String>): FdbFuture<Unit>
    override fun removeIfExists(context: TransactionContext, subpath: List<String>): FdbFuture<Boolean>
    override fun list(context: ReadTransactionContext, subpath: List<String>): FdbFuture<List<String>>
    override fun exists(context: ReadTransactionContext, subpath: List<String>): FdbFuture<Boolean>

    fun asDirectory(): Directory
    override val path: List<String>
    override val layer: ByteArray
    override fun getDirectoryLayer(): DirectoryLayer

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int

    companion object {
        fun forLayout(layout: DirectoryLayout = DirectoryLayout.JAVA_CANONICAL, nodeSubspace: Subspace = DEFAULT_NODE_SUBSPACE, contentSubspace: Subspace = DEFAULT_CONTENT_SUBSPACE, allowManualPrefixes: Boolean = false): DirectoryLayer
        val DEFAULT_NODE_SUBSPACE: Subspace
        val DEFAULT_CONTENT_SUBSPACE: Subspace
        val PARTITION_LAYER: ByteArray
        fun createWithNodeSubspace(nodeSubspace: Subspace): Directory
        fun createWithContentSubspace(contentSubspace: Subspace): Directory
        fun getDefault(): DirectoryLayer
        fun from(
            nodeSubspace: Subspace,
            contentSubspace: Subspace = Subspace(),
            allowManualPrefixes: Boolean = false
        ): DirectoryLayer
    }
}
