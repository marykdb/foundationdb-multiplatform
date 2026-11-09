package maryk.foundationdb.directory

import maryk.foundationdb.Range
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.StreamingMode
import maryk.foundationdb.Transaction
import maryk.foundationdb.awaitBlocking
import maryk.foundationdb.collectRange
import maryk.foundationdb.nextKey
import maryk.foundationdb.subspace.Subspace

internal object DirectoryStore {
    private val VERSION_BYTES = byteArrayOf(
        1, 0, 0, 0, // major
        0, 0, 0, 0, // minor
        0, 0, 0, 0  // patch
    )

    // Matches stored metadata: child pointers are at tuple (0, name)
    private const val SUB_DIR_KEY = 0
    private val VERSION_KEY = "version".encodeToByteArray()
    private val LAYER_KEY = "layer".encodeToByteArray()
    private val HCA_KEY = "hca".encodeToByteArray()
    private val PARTITION_LAYER = "partition".encodeToByteArray()

    private data class Node(
        val subspace: Subspace?,
        val path: List<String>,
        val layer: ByteArray = byteArrayOf(),
        val exists: Boolean = false
    )

    suspend fun ensureDirectory(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray,
        context: DirectoryContext
    ): DirectorySubspace = createOrOpenInternal(transaction, path, layer, prefixOverride = null, allowCreate = true, allowOpen = true, context = context)

    suspend fun createDirectory(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray,
        prefixOverride: ByteArray? = null,
        context: DirectoryContext
    ): DirectorySubspace = createOrOpenInternal(transaction, path, layer, prefixOverride, allowCreate = true, allowOpen = false, context = context)

    suspend fun openDirectory(readTransaction: ReadTransaction, path: List<String>, layer: ByteArray, context: DirectoryContext): DirectorySubspace? {
        route(readTransaction, path, context, false)?.let { return openDirectory(readTransaction, it.second, layer, it.first) }
        verifyVersion(readTransaction, writeAccess = false, context = context)
        if (path.isEmpty()) throw DirectoryVersionException("Cannot open root directory")
        val node = find(readTransaction, path, context)
        if (!node.exists) return null
        validateLayer(node.layer, layer, path)
        if (node.layer.contentEquals(PARTITION_LAYER)) validatePartitionLayout(readTransaction, dataPrefix(node.subspace!!, context), context, path)
        return buildDirectorySubspace(node, context)
    }

    suspend fun exists(readTransaction: ReadTransaction, path: List<String>, layer: ByteArray, context: DirectoryContext): Boolean {
        route(readTransaction, path, context, false)?.let { return exists(readTransaction, it.second, layer, it.first) }
        verifyVersion(readTransaction, writeAccess = false, context = context)
        val node = find(readTransaction, path, context)
        if (!node.exists) return false
        validateLayer(node.layer, layer, path)
        if (node.layer.contentEquals(PARTITION_LAYER)) validatePartitionLayout(readTransaction, dataPrefix(node.subspace!!, context), context, path)
        return true
    }

    suspend fun remove(transaction: Transaction, path: List<String>, context: DirectoryContext): Boolean {
        requireWritableLayout(context)
        route(transaction, path, context, false)?.let { return remove(transaction, it.second, it.first) }
        verifyVersion(transaction, writeAccess = true, context = context)
        if (path.isEmpty()) throw DirectoryVersionException("Cannot remove root directory")
        val node = find(transaction, path, context)
        if (!node.exists) return false
        if (node.layer.contentEquals(PARTITION_LAYER)) validatePartitionLayout(transaction, dataPrefix(node.subspace!!, context), context, path)
        removeRecursive(transaction, node.subspace!!, context, path)
        removeFromParent(transaction, path, context)
        return true
    }

    suspend fun listChildren(readTransaction: ReadTransaction, path: List<String>, limit: Int, context: DirectoryContext): List<String> {
        route(readTransaction, path, context, true)?.let { return listChildren(readTransaction, it.second, limit, it.first) }
        verifyVersion(readTransaction, writeAccess = false, context = context)
        val node = find(readTransaction, path, context)
        if (!node.exists) throw NoSuchDirectoryException(path)
        val subdir = node.subspace!!.get(SUB_DIR_KEY)
        val range = subdir.range()
        val result = readTransaction.collectRange(range.begin, range.end, limit, reverse = false, streamingMode = StreamingMode.WANT_ALL).await()
        return result.values.map { kv -> subdir.unpack(kv.key).getString(0) }
    }

    suspend fun move(transaction: Transaction, oldPath: List<String>, newPath: List<String>, context: DirectoryContext): DirectorySubspace {
        requireWritableLayout(context)
        val oldRoute = route(transaction, oldPath, context, false)
        val newRoute = route(transaction, newPath, context, false)
        if (oldRoute != null || newRoute != null) {
            if (oldRoute == null || newRoute == null || oldRoute.first.pathPrefix != newRoute.first.pathPrefix) {
                throw DirectoryMoveException(oldPath, newPath, "Cannot move between directory partitions")
            }
            return move(transaction, oldRoute.second, newRoute.second, oldRoute.first)
        }
        verifyVersion(transaction, writeAccess = true, context = context)
        if (newPath.isEmpty()) throw DirectoryMoveException(oldPath, newPath, "Cannot move to root")
        if (oldPath == newPath) throw DirectoryMoveException(oldPath, newPath, "Source and destination must differ")

        val oldNode = find(transaction, oldPath, context)
        val newNode = find(transaction, newPath, context)

        if (!oldNode.exists) throw NoSuchDirectoryException(oldPath)
        if (newNode.exists) throw DirectoryAlreadyExistsException(newPath)

        val newParentPath = newPath.dropLast(1)
        val newParent = find(transaction, newParentPath, context)
        if (!newParent.exists) throw NoSuchDirectoryException(newParentPath)

        val prefix = dataPrefix(oldNode.subspace!!, context)
        val layerData = oldNode.layer
        if (layerData.contentEquals(PARTITION_LAYER)) validatePartitionLayout(transaction, prefix, context, oldPath)

        // update parent pointers
        val parentSubdir = newParent.subspace!!.get(SUB_DIR_KEY)
        val newKey = parentSubdir.pack(newPath.last())
        transaction.set(newKey, prefix)

        removeFromParent(transaction, oldPath, context)

        return buildDirectorySubspace(Node(nodeWithPrefix(prefix, context), newPath, layerData, true), context)
    }

    private suspend fun createOrOpenInternal(
        transaction: Transaction,
        path: List<String>,
        layer: ByteArray,
        prefixOverride: ByteArray?,
        allowCreate: Boolean,
        allowOpen: Boolean,
        context: DirectoryContext
    ): DirectorySubspace {
        route(transaction, path, context, false)?.let {
            return createOrOpenInternal(transaction, it.second, layer, prefixOverride, allowCreate, allowOpen, it.first)
        }
        verifyVersion(transaction, writeAccess = allowCreate && context.layout != DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS, context = context)
        if (path.isEmpty()) throw DirectoryVersionException("Cannot open root directory")

        if (prefixOverride != null && !context.allowManualPrefixes) {
            throw DirectoryVersionException("Manual prefixes not enabled for this directory layer")
        }

        val existing = find(transaction, path, context)
        if (existing.exists) {
            if (existing.layer.contentEquals(PARTITION_LAYER)) validatePartitionLayout(transaction, dataPrefix(existing.subspace!!, context), context, path)
            validateLayer(existing.layer, layer, path)
            if (!allowOpen) throw DirectoryAlreadyExistsException(path)
            return buildDirectorySubspace(existing, context)
        }

        if (!allowCreate) throw NoSuchDirectoryException(path)
        requireWritableLayout(context)

        val parentNode = ensureParent(transaction, path, context)
        val prefix = prefixOverride ?: allocatePrefix(transaction, context)
        if (!isPrefixFree(transaction, prefix, context = context)) {
            throw DirectoryVersionException("Directory prefix not free")
        }
        ensurePrefixEmpty(transaction, prefix)

        val nodeSubspace = nodeWithPrefix(prefix, context)
        val parentSubdir = parentNode.get(SUB_DIR_KEY)
        transaction.set(parentSubdir.pack(path.last()), prefix)
        transaction.set(nodeSubspace.pack(LAYER_KEY), layer)

        return buildDirectorySubspace(Node(nodeSubspace, path, layer, exists = true), context)
    }

    private suspend fun ensureParent(transaction: Transaction, path: List<String>, context: DirectoryContext): Subspace {
        return if (path.size == 1) {
            rootNode(context)
        } else {
            val parentPath = path.dropLast(1)
            ensureDirectory(transaction, parentPath, ByteArray(0), context).let { nodeWithPrefix(it.pack(), context) }
        }
    }

    private suspend fun allocatePrefix(transaction: Transaction, context: DirectoryContext): ByteArray =
        HighContentionAllocator(rootNode(context).get(HCA_KEY)).allocate(transaction).let { context.contentSubspace.getKey() + it }

    private fun nodeWithPrefix(prefix: ByteArray, context: DirectoryContext): Subspace = context.nodeSubspace.get(prefix)

    private fun requireWritableLayout(context: DirectoryContext) {
        if (context.layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) throw DirectoryVersionException("Legacy flat partition layout is read-only; copy and verify it into a canonical layer before writing")
    }

    private suspend fun validatePartitionLayout(readTransaction: ReadTransaction, prefix: ByteArray, context: DirectoryContext, path: List<String>) {
        val flatRange = nodeWithPrefix(prefix, context).get(SUB_DIR_KEY).range()
        val hasFlatChildren = readTransaction.getRange(flatRange, 1).asList().await().isNotEmpty()
        if (context.layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) {
            for (layout in listOf(DirectoryLayout.JAVA_CANONICAL, DirectoryLayout.LEGACY_NATIVE)) {
                val nested = context.copy(layout = layout).partition(prefix, emptyList())
                if (readTransaction.get(rootNode(nested).pack(VERSION_KEY)).await() != null) throw DirectoryVersionException("Mixed flat and nested partition layouts require explicit reconciliation before migration")
            }
            return
        }
        if (hasFlatChildren) throw DirectoryMigrationRequiredException(context.pathPrefix + path, DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS)
        val selected = context.partition(prefix, emptyList())
        val otherLayout = if (context.layout == DirectoryLayout.JAVA_CANONICAL) DirectoryLayout.LEGACY_NATIVE else DirectoryLayout.JAVA_CANONICAL
        val other = context.copy(layout = otherLayout).partition(prefix, emptyList())
        val otherVersion = readTransaction.get(rootNode(other).pack(VERSION_KEY)).await()
        if (otherVersion != null) {
            val selectedVersion = readTransaction.get(rootNode(selected).pack(VERSION_KEY)).await()
            if (selectedVersion == null) {
                if (context.layout == DirectoryLayout.JAVA_CANONICAL) throw DirectoryMigrationRequiredException(context.pathPrefix + path, otherLayout)
                throw DirectoryVersionException("Partition metadata uses the canonical layout; open it through the canonical directory API")
            }
            throw DirectoryVersionException("Partition contains both legacy and canonical metadata; resolve the mixed layout explicitly")
        }
    }

    private suspend fun route(readTransaction: ReadTransaction, path: List<String>, context: DirectoryContext, includeLast: Boolean): Pair<DirectoryContext, List<String>>? {
        val count = if (includeLast) path.size else path.size - 1
        for (size in 1..count) {
            val node = find(readTransaction, path.take(size), context)
            if (!node.exists) return null
            if (node.layer.contentEquals(PARTITION_LAYER)) {
                validatePartitionLayout(readTransaction, dataPrefix(node.subspace!!, context), context, path.take(size))
                if (context.layout == DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) continue
                return context.partition(dataPrefix(node.subspace!!, context), path.take(size)) to path.drop(size)
            }
        }
        return null
    }

    private suspend fun find(readTransaction: ReadTransaction, path: List<String>, context: DirectoryContext): Node {
        var node = Node(subspace = rootNode(context), path = emptyList(), exists = true)
        var index = 0
        while (index < path.size) {
            val subdir = node.subspace!!.get(SUB_DIR_KEY)
            val childKey = subdir.pack(path[index])
            val childPrefix = readTransaction.get(childKey).await()
            val currentPath = path.subList(0, index + 1)
            if (childPrefix == null) {
                return Node(subspace = null, path = currentPath, exists = false)
            }
            val childNode = nodeWithPrefix(childPrefix, context)
            val loaded = loadMetadata(readTransaction, childNode, currentPath)
            if (!loaded.exists) return loaded
            node = loaded
            index++
        }
        return node
    }

    private suspend fun loadMetadata(readTransaction: ReadTransaction, subspace: Subspace, path: List<String>): Node {
        val layerKey = subspace.pack(LAYER_KEY)
        val layerBytes = readTransaction.get(layerKey).await()
        if (layerBytes != null) {
            return Node(subspace, path, layerBytes, true)
        }

        // Keep compatibility with older writers that could omit the layer key, but avoid treating a
        // stale pointer to a fully removed node as an existing directory.
        val hasAnyMetadata = readTransaction.collectRange(
            subspace.range().begin,
            subspace.range().end,
            limit = 1,
            reverse = false,
            streamingMode = StreamingMode.WANT_ALL
        ).await().values.isNotEmpty()
        return Node(subspace, path, byteArrayOf(), hasAnyMetadata)
    }

    private suspend fun removeRecursive(transaction: Transaction, nodeSubspace: Subspace, context: DirectoryContext, path: List<String>) {
        val layer = transaction.get(nodeSubspace.pack(LAYER_KEY)).await()
        if (layer != null && layer.contentEquals(PARTITION_LAYER)) {
            validatePartitionLayout(transaction, dataPrefix(nodeSubspace, context), context, path)
            val nested = context.partition(dataPrefix(nodeSubspace, context), emptyList())
            val nestedSubdir = rootNode(nested).get(SUB_DIR_KEY)
            val children = transaction.collectRange(nestedSubdir.range().begin, nestedSubdir.range().end, 0, false).await()
            for (child in children.values) removeRecursive(transaction, nodeWithPrefix(child.value, nested), nested, path + nestedSubdir.unpack(child.key).getString(0))
            transaction.clear(nested.nodeSubspace.range())
        }
        val subdir = nodeSubspace.get(SUB_DIR_KEY)
        val range = subdir.range()
        val children = transaction.collectRange(range.begin, range.end, 0, reverse = false, streamingMode = StreamingMode.WANT_ALL).await()
        for (kv in children.values) {
            val childPrefix = kv.value
            val childNode = nodeWithPrefix(childPrefix, context)
            removeRecursive(transaction, childNode, context, path + subdir.unpack(kv.key).getString(0))
        }

        val prefix = dataPrefix(nodeSubspace, context)
        transaction.clear(Range(prefix, prefix.nextKey()))
        transaction.clear(nodeSubspace.range())
    }

    private fun removeFromParent(transaction: Transaction, path: List<String>, context: DirectoryContext) {
        val parentPath = path.dropLast(1)
        val parentNode = if (parentPath.isEmpty()) {
            rootNode(context)
        } else {
            nodeWithPrefix(findPrefixFromPath(transaction, parentPath, context) ?: return, context)
        }
        val subdir = parentNode.get(SUB_DIR_KEY)
        transaction.clear(subdir.pack(path.last()))
    }

    private fun findPrefixFromPath(readTransaction: Transaction, path: List<String>, context: DirectoryContext): ByteArray? {
        var node = rootNode(context)
        for (segment in path) {
            val subdir = node.get(SUB_DIR_KEY)
            val childKey = subdir.pack(segment)
            val childPrefix = runBlockingGet(readTransaction, childKey) ?: return null
            node = nodeWithPrefix(childPrefix, context)
        }
        return dataPrefix(node, context)
    }

    private fun runBlockingGet(transaction: Transaction, key: ByteArray): ByteArray? =
        transaction.get(key).awaitBlocking()

    private fun dataPrefix(nodeSubspace: Subspace, context: DirectoryContext): ByteArray =
        context.nodeSubspace.unpack(nodeSubspace.getKey()).getBytes(0)

    private fun validateLayer(stored: ByteArray, requested: ByteArray, path: List<String>) {
        if (requested.isEmpty()) return
        if (!stored.contentEquals(requested)) {
            throw MismatchedLayerException(path, stored, requested)
        }
    }

    private suspend fun verifyVersion(readTransaction: ReadTransaction, writeAccess: Boolean, context: DirectoryContext) {
        val key = rootNode(context).pack(VERSION_KEY)
        val stored = readTransaction.get(key).await()
        if (stored == null) {
            if (writeAccess) {
                if (readTransaction !is Transaction) {
                    throw DirectoryVersionException("Directory layer not initialized")
                }
                readTransaction.set(key, VERSION_BYTES)
            }
            return
        }
        if (stored.size < VERSION_BYTES.size) throw DirectoryVersionException("Truncated directory layer version")
        fun component(offset: Int): Int = (0 until 4).fold(0) { value, index -> value or ((stored[offset + index].toInt() and 255) shl (index * 8)) }
        if (component(0) > 1) throw DirectoryVersionException("Directory layer major version is not supported")
        if (writeAccess && component(4) > 0) throw DirectoryVersionException("Directory layer minor version is read-only")
    }

    private suspend fun ensurePrefixEmpty(transaction: Transaction, prefix: ByteArray) {
        val range = Range(prefix, prefix.nextKey())
        val existing = transaction.collectRange(range.begin, range.end, 1, reverse = false).await()
        if (existing.values.isNotEmpty()) {
            throw DirectoryVersionException("Directory prefix not empty")
        }
    }

    private suspend fun isPrefixFree(transaction: Transaction, prefix: ByteArray, context: DirectoryContext): Boolean {
        if (prefix.isEmpty()) return false
        // prefix cannot overlap the directory metadata subspace
        if (prefix.startsWith(context.nodeSubspace.getKey())) return false

        val containingNode = nodeContainingKey(transaction, prefix, context)
        if (containingNode != null) return false
        val begin = context.nodeSubspace.pack(prefix)
        val end = context.nodeSubspace.pack(prefix.nextKey())
        val overlaps = transaction.collectRange(begin, end, 1, reverse = false).await()
        return overlaps.values.isEmpty()
    }

    private suspend fun nodeContainingKey(readTransaction: ReadTransaction, key: ByteArray, context: DirectoryContext): Subspace? {
        if (key.startsWith(context.nodeSubspace.getKey())) {
            return rootNode(context)
        }
        val range = Range(context.nodeSubspace.range().begin, context.nodeSubspace.pack(key).nextKey())
        val result = readTransaction.collectRange(range.begin, range.end, 1, reverse = true, streamingMode = StreamingMode.WANT_ALL).await()
        if (result.values.isNotEmpty()) {
            val prevPrefix = context.nodeSubspace.unpack(result.values[0].key).getBytes(0)
            if (key.startsWith(prevPrefix)) {
                return nodeWithPrefix(prevPrefix, context)
            }
        }
        return null
    }

    private fun rootNode(context: DirectoryContext): Subspace =
        nodeWithPrefix(context.nodeSubspace.getKey(), context)

    private fun buildDirectorySubspace(node: Node, context: DirectoryContext): DirectorySubspace {
        val prefix = dataPrefix(node.subspace!!, context)
        return if (node.layer.contentEquals(PARTITION_LAYER)) {
            PartitionDirectorySubspace(prefix, context.pathPrefix + node.path, node.layer, context)
        } else {
            DirectorySubspace(prefix, context.pathPrefix + node.path, node.layer, context)
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
}
