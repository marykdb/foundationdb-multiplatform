package maryk.foundationdb.directory

import maryk.foundationdb.ByteArrayUtil
import maryk.foundationdb.Range
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.StreamingMode
import maryk.foundationdb.collectRange
import maryk.foundationdb.nextKey
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.Tuple.Companion.fromList

internal object DirectoryStore {
    private val BASE = byteArrayOf(0x02) // user-space prefix
    private val META_TAG = byteArrayOf(0x6D) // 'm'
    private val CHILD_TAG = byteArrayOf(0x63) // 'c'
    private val VERSION_TAG = byteArrayOf(0x76) // 'v'
    private val VERSION_VALUE = byteArrayOf(1, 0, 0)

    private data class DirectoryMetadata(
        val prefix: ByteArray,
        val layer: ByteArray
    )

    private fun encoded(path: List<String>): ByteArray = fromList(path).pack()

    fun metadataKey(path: List<String>, layer: ByteArray): ByteArray =
        ByteArrayUtil.join(BASE, META_TAG, encoded(path))

    private fun versionKey(): ByteArray =
        ByteArrayUtil.join(BASE, VERSION_TAG)

    fun prefixFromPath(path: List<String>, layer: ByteArray): ByteArray =
        ByteArrayUtil.join(layer, fromList(path).pack())

    suspend fun ensureDirectory(transaction: Transaction, path: List<String>, layer: ByteArray): DirectorySubspace {
        transaction.ensureDirectoryLayerVersion(layer)
        val key = metadataKey(path, layer)
        val existing = transaction.get(key).await()
        val (prefix, resolvedLayer) = if (existing == null) {
            val generated = prefixFromPath(path, layer)
            writeMetadata(transaction, key, encodeMetadata(generated, layer))
            registerChild(transaction, path, layer)
            generated to layer
        } else {
            val metadata = decodeMetadata(existing, path)
            validateLayer(metadata.layer, layer, path)
            metadata.prefix to metadata.layer
        }
        return DirectorySubspace(prefix, path, resolvedLayer)
    }

    suspend fun createDirectory(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray,
        prefixOverride: ByteArray? = null
    ): DirectorySubspace {
        transaction.ensureDirectoryLayerVersion(layer)
        val key = metadataKey(path, layer)
        val existing = transaction.get(key).await()
        if (existing != null) throw DirectoryAlreadyExistsException(path)
        val prefix = (prefixOverride ?: prefixFromPath(path, layer)).copyOf()
        writeMetadata(transaction, key, encodeMetadata(prefix, layer))
        registerChild(transaction, path, layer)
        return DirectorySubspace(prefix, path, layer)
    }

    suspend fun openDirectory(readTransaction: ReadTransaction, path: List<String>, layer: ByteArray): DirectorySubspace? {
        readTransaction.verifyDirectoryLayerVersion(layer)
        val key = metadataKey(path, layer)
        val stored = readTransaction.get(key).await() ?: return null
        val metadata = decodeMetadata(stored, path)
        validateLayer(metadata.layer, layer, path)
        return DirectorySubspace(metadata.prefix, path, metadata.layer)
    }

    suspend fun exists(readTransaction: ReadTransaction, path: List<String>, layer: ByteArray): Boolean {
        readTransaction.verifyDirectoryLayerVersion(layer)
        val stored = readTransaction.get(metadataKey(path, layer)).await() ?: return false
        // Validate layer when the caller specifies one.
        val metadata = decodeMetadata(stored, path)
        validateLayer(metadata.layer, layer, path)
        return true
    }

    suspend fun exists(transaction: Transaction, path: List<String>, layer: ByteArray): Boolean {
        transaction.ensureDirectoryLayerVersion(layer)
        return transaction.get(metadataKey(path, layer)).await() != null
    }

    suspend fun remove(transaction: Transaction, path: List<String>, layer: ByteArray): Boolean {
        transaction.ensureDirectoryLayerVersion(layer)
        val key = metadataKey(path, layer)
        val existed = transaction.get(key).await() != null
        if (!existed) return false
        transaction.clear(key)
        val range = rangeFor(path, layer)
        transaction.clear(range.begin, range.end)
        unregisterChild(transaction, path, layer)
        return true
    }

    suspend fun listChildren(readTransaction: ReadTransaction, path: List<String>, layer: ByteArray, limit: Int): List<String> {
        readTransaction.verifyDirectoryLayerVersion(layer)
        val range = childRange(path, layer)
        val result = readTransaction.collectRange(range.begin, range.end, limit, reverse = false, streamingMode = StreamingMode.WANT_ALL).await()
        val prefix = childPrefix(path, layer)
        val offset = prefix.size
        return result.values.mapNotNull { kv ->
            if (kv.key.size <= offset) return@mapNotNull null
            val tupleBytes = kv.key.copyOfRange(offset, kv.key.size)
            val entry = Tuple.fromBytes(tupleBytes)
            entry.items.lastOrNull() as? String
        }.distinct()
    }

    suspend fun move(transaction: Transaction, oldPath: List<String>, newPath: List<String>, layer: ByteArray): DirectorySubspace {
        transaction.ensureDirectoryLayerVersion(layer)
        validateMovePaths(oldPath, newPath)
        val oldRange = rangeFor(oldPath, layer)
        val entries = transaction.collectRange(
            oldRange.begin,
            oldRange.end,
            0,
            reverse = false,
            streamingMode = StreamingMode.WANT_ALL
        ).await().values
        val metadataEntryKey = metadataKey(oldPath, layer)
        val metadataEntry = entries.firstOrNull { it.key.contentEquals(metadataEntryKey) }
            ?: throw DirectoryVersionException("Corrupt directory metadata at ${DirectoryUtil.pathStr(oldPath)}")
        val metadata = decodeMetadata(metadataEntry.value, oldPath)

        transaction.clear(oldRange.begin, oldRange.end)
        entries.forEach { kv ->
            val relative = kv.key.copyOfRange(rangePrefixLength(oldPath, layer), kv.key.size)
            val newKey = ByteArrayUtil.join(metadataKey(newPath, layer), relative)
            transaction.set(newKey, kv.value)
        }
        unregisterChild(transaction, oldPath, layer)
        registerChild(transaction, newPath, layer)
        return DirectorySubspace(metadata.prefix, newPath, metadata.layer)
    }

    private fun rangeFor(path: List<String>, layer: ByteArray): Range {
        val start = metadataKey(path, layer)
        val end = start.nextKey()
        return Range(start, end)
    }

    private fun childPrefix(path: List<String>, layer: ByteArray): ByteArray =
        ByteArrayUtil.join(BASE, CHILD_TAG, encoded(path))

    private fun childRange(path: List<String>, layer: ByteArray): Range {
        val start = childPrefix(path, layer)
        val end = start.nextKey()
        return Range(start, end)
    }

    private fun rangePrefixLength(path: List<String>, layer: ByteArray): Int =
        metadataTupleOffset() + fromList(path).pack().size

    private fun metadataTupleOffset(): Int =
        BASE.size + META_TAG.size

    private fun childEntryKey(parent: List<String>, layer: ByteArray, child: String): ByteArray =
        ByteArrayUtil.join(childPrefix(parent, layer), Tuple.from(child).pack())

    private fun registerChild(transaction: Transaction, path: List<String>, layer: ByteArray) {
        if (path.isEmpty()) return
        val parent = path.dropLast(1)
        val child = path.last()
        val key = childEntryKey(parent, layer, child)
        transaction.set(key, byteArrayOf(1))
    }

    private fun unregisterChild(transaction: Transaction, path: List<String>, layer: ByteArray) {
        if (path.isEmpty()) return
        val parent = path.dropLast(1)
        val child = path.last()
        val key = childEntryKey(parent, layer, child)
        transaction.clear(key)
    }

    private fun Transaction.prepareDirectoryWrite() {
    }

    private fun ReadTransaction.prepareDirectoryRead() {
    }

    private fun writeMetadata(
        transaction: Transaction,
        key: ByteArray,
        value: ByteArray
    ) {
        transaction.set(key, value)
    }

    private fun encodeMetadata(prefix: ByteArray, layer: ByteArray): ByteArray =
        Tuple.from(prefix, layer).pack()

    private fun decodeMetadata(bytes: ByteArray, path: List<String>): DirectoryMetadata = try {
        val tuple = Tuple.fromBytes(bytes)
        val prefix = tuple.items.getOrNull(0) as? ByteArray
            ?: throw IllegalStateException("Directory metadata missing prefix")
        val storedLayer = tuple.items.getOrNull(1) as? ByteArray ?: byteArrayOf()
        DirectoryMetadata(prefix, storedLayer)
    } catch (t: Throwable) {
        throw DirectoryVersionException("Corrupt directory metadata at ${DirectoryUtil.pathStr(path)}")
    }

    private fun validateLayer(stored: ByteArray, requested: ByteArray, path: List<String>) {
        if (stored.isEmpty() || requested.isEmpty()) return
        if (!stored.contentEquals(requested)) {
            throw MismatchedLayerException(path, stored, requested)
        }
    }

    private suspend fun Transaction.ensureDirectoryLayerVersion(layer: ByteArray) {
        prepareDirectoryWrite()
        val key = versionKey()
        val stored = this.get(key).await()
        if (stored == null) {
            this.set(key, VERSION_VALUE)
        } else if (!stored.contentEquals(VERSION_VALUE)) {
            throw DirectoryVersionException("Directory layer version mismatch")
        }
    }

    private suspend fun ReadTransaction.verifyDirectoryLayerVersion(layer: ByteArray) {
        prepareDirectoryRead()
        val key = versionKey()
        val stored = this.get(key).await()
        if (stored != null && !stored.contentEquals(VERSION_VALUE)) {
            throw DirectoryVersionException("Directory layer version mismatch")
        }
    }

    private fun validateMovePaths(oldPath: List<String>, newPath: List<String>) {
        if (oldPath == newPath) {
            throw DirectoryMoveException(oldPath, newPath, "Source and destination must differ")
        }
        if (newPath.startsWith(oldPath)) {
            throw DirectoryMoveException(oldPath, newPath, "Cannot move a directory inside itself")
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
