package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.fdbFutureFromSuspend

internal class DirectoryAdapter(
    override val path: List<String>,
    override val layer: ByteArray,
    private val context: DirectoryContext
) : Directory {
    constructor(path: List<String> = emptyList(), layer: ByteArray = byteArrayOf()) : this(path, layer, DirectoryContext.default())
    override fun getDirectoryLayer(): DirectoryLayer = DirectoryLayer(context)

    private val relativePath: List<String> get() = path.drop(context.pathPrefix.size)

    override fun createOrOpen(context: TransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            DirectoryStore.ensureDirectory(tr, relativePath + subpath, layer, this.context)
        }

    override fun open(context: ReadTransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        runWithReadContext(context) { rt ->
            DirectoryStore.openDirectory(rt, relativePath + subpath, layer, this.context)
                ?: throw NoSuchDirectoryException(relativePath + subpath)
        }

    override fun create(
        context: TransactionContext,
        subpath: List<String>,
        layer: ByteArray,
        prefix: ByteArray?
    ): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            DirectoryStore.createDirectory(tr, relativePath + subpath, layer, prefix, this.context)
        }

    override fun moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            if (newPath.take(this.context.pathPrefix.size) != this.context.pathPrefix) throw DirectoryMoveException(path, newPath, "Cannot move between directory partitions")
            val destination = newPath.drop(this.context.pathPrefix.size)
            validateMovePaths(relativePath, destination)
            if (!DirectoryStore.exists(tr, relativePath, byteArrayOf(), this.context)) throw NoSuchDirectoryException(path)
            if (DirectoryStore.exists(tr, destination, byteArrayOf(), this.context)) throw DirectoryAlreadyExistsException(newPath)
            DirectoryStore.move(tr, relativePath, destination, this.context)
        }

    override fun move(
        context: TransactionContext,
        oldSubpath: List<String>,
        newSubpath: List<String>
    ): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            val source = relativePath + oldSubpath
            val dest = relativePath + newSubpath
            validateMovePaths(source, dest)
            if (!DirectoryStore.exists(tr, source, byteArrayOf(), this.context)) throw NoSuchDirectoryException(source)
            if (DirectoryStore.exists(tr, dest, byteArrayOf(), this.context)) throw DirectoryAlreadyExistsException(dest)
            DirectoryStore.move(tr, source, dest, this.context)
        }

    override fun remove(context: TransactionContext, subpath: List<String>): FdbFuture<Unit> =
        runWithTransactionContext(context) { tr ->
            if (!DirectoryStore.remove(tr, relativePath + subpath, this.context)) {
                throw NoSuchDirectoryException(relativePath + subpath)
            }
        }

    override fun removeIfExists(context: TransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        runWithTransactionContext(context) { tr ->
            DirectoryStore.remove(tr, relativePath + subpath, this.context)
        }

    override fun list(context: ReadTransactionContext, subpath: List<String>): FdbFuture<List<String>> =
        runWithReadContext(context) { rt ->
            DirectoryStore.listChildren(rt, relativePath + subpath, 0, this.context)
        }

    override fun exists(context: ReadTransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        runWithReadContext(context) { rt ->
            DirectoryStore.exists(rt, relativePath + subpath, byteArrayOf(), this.context)
        }

    private fun <T> runWithTransactionContext(context: TransactionContext, block: suspend (Transaction) -> T): FdbFuture<T> =
        when (context) {
            is Transaction -> fdbFutureFromSuspend { block(context) }
            else -> context.runAsync { txn -> fdbFutureFromSuspend { block(txn) } }
        }

    private fun <T> runWithReadContext(context: ReadTransactionContext, block: suspend (ReadTransaction) -> T): FdbFuture<T> =
        when (context) {
            is ReadTransaction -> fdbFutureFromSuspend { block(context) }
            is Transaction -> fdbFutureFromSuspend { block(context) }
            else -> context.readAsync { rt -> fdbFutureFromSuspend { block(rt) } }
        }

    private fun validateMovePaths(source: List<String>, dest: List<String>) {
        if (source.isEmpty()) {
            throw DirectoryMoveException(source, dest, "The root directory cannot be moved")
        }
        if (source == dest) {
            throw DirectoryMoveException(source, dest, "Source and destination must differ")
        }
        if (dest.startsWith(source)) {
            throw DirectoryMoveException(source, dest, "Cannot move a directory inside itself")
        }
    }

    private fun List<String>.startsWith(prefix: List<String>): Boolean {
        if (prefix.isEmpty() || this.size < prefix.size) return false
        for (idx in prefix.indices) {
            if (this[idx] != prefix[idx]) return false
        }
        return true
    }
}

internal fun Directory(path: List<String>, layer: ByteArray, context: DirectoryContext): Directory = DirectoryAdapter(path, layer, context)
