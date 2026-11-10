package maryk.foundationdb.directory

import com.apple.foundationdb.directory.MismatchedLayerException as JavaMismatchedLayerException

import com.apple.foundationdb.directory.NoSuchDirectoryException as JavaNoSuchDirectoryException

import com.apple.foundationdb.directory.DirectoryVersionException as JavaDirectoryVersionException

import com.apple.foundationdb.directory.DirectoryMoveException as JavaDirectoryMoveException

import com.apple.foundationdb.directory.DirectoryAlreadyExistsException as JavaDirectoryAlreadyExistsException

import com.apple.foundationdb.directory.Directory as JavaDirectory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import maryk.foundationdb.mapToFdbFuture
import maryk.foundationdb.asCompletableFuture
import maryk.foundationdb.toFdbFuture
import com.apple.foundationdb.ReadTransactionContext as JavaReadContext
import com.apple.foundationdb.TransactionContext as JavaTransactionContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

internal class DirectoryAdapter(
    internal val delegate: JavaDirectory
) : Directory {
    override fun getDirectoryLayer(): DirectoryLayer = DirectoryLayer(delegate.directoryLayer)

    override val path: List<String>
        get() = delegate.path

    override val layer: ByteArray
        get() = delegate.layer

    override fun createOrOpen(context: TransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        directoryWrite(context) { delegate.createOrOpen(it, subpath, layer) }
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    override fun open(context: ReadTransactionContext, subpath: List<String>, layer: ByteArray): FdbFuture<DirectorySubspace> =
        directoryRead(context) { delegate.open(it, subpath, layer) }
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    override fun create(context: TransactionContext, subpath: List<String>, layer: ByteArray, prefix: ByteArray?): FdbFuture<DirectorySubspace> =
        directoryWrite(context) { delegate.create(it, subpath, layer, prefix) }
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    override fun moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace> =
        directoryWrite(context) { delegate.moveTo(it, newPath) }
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    override fun move(context: TransactionContext, oldSubpath: List<String>, newSubpath: List<String>): FdbFuture<DirectorySubspace> =
        directoryWrite(context) { delegate.move(it, oldSubpath, newSubpath) }
            .mapDirectoryExceptions()
            .mapToFdbFuture(::DirectorySubspace)

    override fun remove(context: TransactionContext, subpath: List<String>): FdbFuture<Unit> =
        directoryWrite(context) { delegate.remove(it, subpath) }
            .mapDirectoryExceptions()
            .mapToFdbFuture { Unit }

    override fun removeIfExists(context: TransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        directoryWrite(context) { delegate.removeIfExists(it, subpath) }
            .mapDirectoryExceptions()
            .toFdbFuture()

    override fun list(context: ReadTransactionContext, subpath: List<String>): FdbFuture<List<String>> =
        directoryRead(context) { delegate.list(it, subpath) }
            .mapDirectoryExceptions()
            .toFdbFuture()

    override fun exists(context: ReadTransactionContext, subpath: List<String>): FdbFuture<Boolean> =
        directoryRead(context) { delegate.exists(it, subpath) }
            .mapDirectoryExceptions()
            .toFdbFuture()
}

private fun <T> directoryWrite(context: TransactionContext, operation: (JavaTransactionContext) -> CompletableFuture<T>): CompletableFuture<T> = try { when (context) {
    is Transaction -> operation(context.delegate)
    else -> context.runAsync { operation(it.delegate).toFdbFuture() }.asCompletableFuture()
} } catch (error: Throwable) { CompletableFuture.failedFuture(error.toCommonDirectoryException()) }

private fun <T> directoryRead(context: ReadTransactionContext, operation: (JavaReadContext) -> CompletableFuture<T>): CompletableFuture<T> = try { when (context) {
    is Transaction -> operation(context.delegate)
    is ReadTransaction -> operation(context.delegate)
    else -> context.readAsync { operation(it.delegate).toFdbFuture() }.asCompletableFuture()
} } catch (error: Throwable) { CompletableFuture.failedFuture(error.toCommonDirectoryException()) }

internal fun <T> CompletableFuture<T>.mapDirectoryExceptions(): CompletableFuture<T> {
    val mapped = handle { value, throwable ->
        if (throwable == null) {
            value
        } else {
            throw throwable.toCommonDirectoryException()
        }
    }

    mapped.whenComplete { _, _ -> if (mapped.isCancelled) cancel(true) }
    return mapped
}

private fun Throwable.toCommonDirectoryException(): Throwable {
    val root = (this as? CompletionException)?.cause ?: this
    return when (root) {
        is JavaDirectoryAlreadyExistsException ->
            DirectoryAlreadyExistsException(root.path)
        is JavaNoSuchDirectoryException ->
            NoSuchDirectoryException(root.path)
        is JavaMismatchedLayerException ->
            MismatchedLayerException(root.path, root.stored, root.opened)
        is JavaDirectoryMoveException ->
            DirectoryMoveException(root.sourcePath, root.destPath, root.message ?: "Invalid directory move")
        is JavaDirectoryVersionException ->
            DirectoryVersionException(root.message ?: "Directory layer version mismatch")
        else -> root
    }
}

internal fun Directory(delegate: JavaDirectory): Directory = DirectoryAdapter(delegate)
