package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.fdbFutureFromSuspend

actual class Directory(
    actual val path: List<String> = emptyList(),
    actual val layer: ByteArray = byteArrayOf()
) {
    private val layerData: ByteArray get() = layer

    actual fun createOrOpen(context: TransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            DirectoryStore.ensureDirectory(tr, path + subpath, layer)
        }

    actual fun open(context: ReadTransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        runWithReadContext(context) { rt ->
            DirectoryStore.openDirectory(rt, path + subpath, layer)
                ?: throw NoSuchDirectoryException(path + subpath)
        }

    actual fun create(
        context: TransactionContext,
        subpath: List<String>,
        layer: ByteArray,
        prefix: ByteArray?
    ): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            DirectoryStore.createDirectory(tr, path + subpath, layer, prefix)
        }

    actual fun moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            validateMovePaths(path, newPath)
            if (!DirectoryStore.exists(tr, path, layerData)) throw NoSuchDirectoryException(path)
            if (DirectoryStore.exists(tr, newPath, layerData)) throw DirectoryAlreadyExistsException(newPath)
            DirectoryStore.move(tr, path, newPath, layerData)
        }

    actual fun move(
        context: TransactionContext,
        oldSubpath: List<String>,
        newSubpath: List<String>
    ): FdbFuture<DirectorySubspace> =
        runWithTransactionContext(context) { tr ->
            val source = path + oldSubpath
            val dest = path + newSubpath
            validateMovePaths(source, dest)
            if (!DirectoryStore.exists(tr, source, layerData)) throw NoSuchDirectoryException(source)
            if (DirectoryStore.exists(tr, dest, layerData)) throw DirectoryAlreadyExistsException(dest)
            DirectoryStore.move(tr, source, dest, layerData)
        }

    actual fun remove(context: TransactionContext, subpath: List<String>): FdbFuture<Unit> =
        runWithTransactionContext(context) { tr ->
            if (!DirectoryStore.remove(tr, path + subpath, layerData)) {
                throw NoSuchDirectoryException(path + subpath)
            }
        }

    actual fun removeIfExists(context: TransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        runWithTransactionContext(context) { tr ->
            DirectoryStore.remove(tr, path + subpath, layerData)
        }

    actual fun list(context: ReadTransactionContext, subpath: List<String>): FdbFuture<List<String>> =
        runWithReadContext(context) { rt ->
            DirectoryStore.listChildren(rt, path + subpath, layerData, 0)
        }

    actual fun exists(context: ReadTransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        runWithReadContext(context) { rt ->
            DirectoryStore.exists(rt, path + subpath, layerData)
        }
    private fun <T> runWithTransactionContext(context: TransactionContext, block: suspend (Transaction) -> T): FdbFuture<T> =
        context.runAsync { txn -> fdbFutureFromSuspend { block(txn) } }

    private fun <T> runWithReadContext(context: ReadTransactionContext, block: suspend (ReadTransaction) -> T): FdbFuture<T> =
        context.readAsync { rt -> fdbFutureFromSuspend { block(rt) } }

    private fun validateMovePaths(source: List<String>, dest: List<String>) {
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
