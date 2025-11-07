package maryk.foundationdb.directory

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import maryk.foundationdb.Database
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.Tenant
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.toFdbFuture

actual class Directory internal constructor(
    internal val delegate: com.apple.foundationdb.directory.Directory
) {
    actual val path: List<String>
        get() = delegate.path

    actual val layer: ByteArray
        get() = delegate.layer

    actual fun createOrOpen(context: TransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        delegate.createOrOpen(unwrapTransactionContext(context), subpath, layer)
            .mapDirectoryExceptions()
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun open(context: ReadTransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        delegate.open(unwrapReadContext(context), subpath, layer)
            .mapDirectoryExceptions()
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun create(context: TransactionContext, subpath: List<String>, layer: ByteArray, prefix: ByteArray?): FdbFuture<DirectorySubspace> =
        delegate.create(unwrapTransactionContext(context), subpath, layer, prefix)
            .mapDirectoryExceptions()
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace> =
        delegate.moveTo(unwrapTransactionContext(context), newPath)
            .mapDirectoryExceptions()
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun move(context: TransactionContext, oldSubpath: List<String>, newSubpath: List<String>): FdbFuture<DirectorySubspace> =
        delegate.move(unwrapTransactionContext(context), oldSubpath, newSubpath)
            .mapDirectoryExceptions()
            .thenApply(::DirectorySubspace)
            .toFdbFuture()

    actual fun remove(context: TransactionContext, subpath: List<String>): FdbFuture<Unit> =
        delegate.remove(unwrapTransactionContext(context), subpath)
            .mapDirectoryExceptions()
            .thenApply { Unit }
            .toFdbFuture()

    actual fun removeIfExists(context: TransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        delegate.removeIfExists(unwrapTransactionContext(context), subpath)
            .toFdbFuture()

    actual fun list(context: ReadTransactionContext, subpath: List<String>): FdbFuture<List<String>> =
        delegate.list(unwrapReadContext(context), subpath)
            .toFdbFuture()

    actual fun exists(context: ReadTransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        delegate.exists(unwrapReadContext(context), subpath)
            .toFdbFuture()
}

private fun unwrapTransactionContext(context: TransactionContext): com.apple.foundationdb.TransactionContext = when (context) {
    is Database -> context.delegate
    is Transaction -> context.delegate
    is Tenant -> context.delegate
    else -> throw IllegalArgumentException("Unsupported TransactionContext type: ${context::class}")
}

private fun unwrapReadContext(context: ReadTransactionContext): com.apple.foundationdb.ReadTransactionContext = when (context) {
    is Database -> context.delegate
    is Transaction -> context.delegate
    is Tenant -> context.delegate
    is maryk.foundationdb.ReadTransaction -> context.delegate
    else -> throw IllegalArgumentException("Unsupported ReadTransactionContext type: ${context::class}")
}

private fun <T> CompletableFuture<T>.mapDirectoryExceptions(): CompletableFuture<T> =
    this.handle { value, throwable ->
        if (throwable == null) {
            value
        } else {
            throw throwable.toCommonDirectoryException()
        }
    }

private fun Throwable.toCommonDirectoryException(): Throwable {
    val root = (this as? CompletionException)?.cause ?: this
    return when (root) {
        is com.apple.foundationdb.directory.DirectoryAlreadyExistsException ->
            DirectoryAlreadyExistsException(root.path)
        is com.apple.foundationdb.directory.NoSuchDirectoryException ->
            NoSuchDirectoryException(root.path)
        is com.apple.foundationdb.directory.MismatchedLayerException ->
            MismatchedLayerException(root.path, root.stored, root.opened)
        is com.apple.foundationdb.directory.DirectoryMoveException ->
            DirectoryMoveException(root.sourcePath, root.destPath, root.message ?: "Invalid directory move")
        is com.apple.foundationdb.directory.DirectoryVersionException ->
            DirectoryVersionException(root.message ?: "Directory layer version mismatch")
        else -> root
    }
}
