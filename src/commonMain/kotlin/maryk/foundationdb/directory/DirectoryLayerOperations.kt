package maryk.foundationdb.directory

import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransactionContext
import maryk.foundationdb.TransactionContext

fun DirectoryLayer.createOrOpen(context: TransactionContext, path: List<String>, layer: ByteArray = byteArrayOf()): FdbFuture<DirectorySubspace> = asDirectory().createOrOpen(context, path, layer)
fun DirectoryLayer.create(context: TransactionContext, path: List<String>, layer: ByteArray = byteArrayOf(), prefix: ByteArray? = null): FdbFuture<DirectorySubspace> = asDirectory().create(context, path, layer, prefix)
fun DirectoryLayer.open(context: ReadTransactionContext, path: List<String>, layer: ByteArray = byteArrayOf()): FdbFuture<DirectorySubspace> = asDirectory().open(context, path, layer)
fun DirectoryLayer.moveTo(context: TransactionContext, newPath: List<String>): FdbFuture<DirectorySubspace> = asDirectory().moveTo(context, newPath)
fun DirectoryLayer.move(context: TransactionContext, oldPath: List<String>, newPath: List<String>): FdbFuture<DirectorySubspace> = asDirectory().move(context, oldPath, newPath)
fun DirectoryLayer.remove(context: TransactionContext, path: List<String> = emptyList()): FdbFuture<Unit> = asDirectory().remove(context, path)
fun DirectoryLayer.removeIfExists(context: TransactionContext, path: List<String> = emptyList()): FdbFuture<Boolean> = asDirectory().removeIfExists(context, path)
fun DirectoryLayer.list(context: ReadTransactionContext, path: List<String> = emptyList()): FdbFuture<List<String>> = asDirectory().list(context, path)
fun DirectoryLayer.exists(context: ReadTransactionContext, path: List<String> = emptyList()): FdbFuture<Boolean> = asDirectory().exists(context, path)
