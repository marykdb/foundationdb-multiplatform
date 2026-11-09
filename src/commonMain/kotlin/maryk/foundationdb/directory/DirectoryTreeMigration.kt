package maryk.foundationdb.directory

import maryk.foundationdb.Database
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.ByteArrayUtil
import maryk.foundationdb.KeyValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple

/**
 * Offline copy into a separate directory layer/content namespace. Stop all source/destination writers.
 * Directory metadata is recreated through the destination layer, including partition boundaries;
 * metadata pointers are never copied as raw data. Source data is never deleted.
 * Inspect each path, review its Entry, then copy bounded batches in committed write transactions.
 * Process Entry.children recursively. Persist Progress.nextSuffix only AFTER its transaction commits.
 * Verify the destination tree before application cutover; retain the source for rollback.
 */
class DirectoryTreeMigration(source: Configuration, destination: Configuration) {
    /** Explicit configuration prevents accidental metadata/content namespace overlap. */
    class Configuration(nodeSubspace: Subspace, contentSubspace: Subspace, val layout: DirectoryLayout = DirectoryLayout.JAVA_CANONICAL) {
        internal val node = nodeSubspace.getKey().copyOf()
        internal val content = contentSubspace.getKey().copyOf()
        internal fun open(): Directory = DirectoryLayer.forLayout(layout, Subspace(node), Subspace(content))
    }
    private val source: Directory = source.open()
    private val destination: Directory = destination.open()
    private val sourceNode = source.node
    private val sourceLayout = source.layout
    private val destinationLayout = destination.layout
    private val destinationNode = destination.node
    private val sourceContent = source.content
    private val destinationContent = destination.content
    private val markerKey = destination.node + Tuple.from("foundationdb-multiplatform.offline-migration").pack()
    private val markerValue = Tuple.from(source.node, source.content, source.layout.name, destination.node, destination.content, destination.layout.name).pack()
    init {
        require(destination.layout != DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) { "Flat legacy layout is a read-only migration source" }
        val prefixes = listOf(source.node, destination.node, destination.content) + listOf(source.content).filter { it.isNotEmpty() }
        require(source.node.isNotEmpty() && destination.node.isNotEmpty() && destination.content.isNotEmpty()) { "Metadata and destination content namespaces must be nonempty" }
        for (index in prefixes.indices) for (other in index + 1 until prefixes.size) {
            require(!ByteArrayUtil.startsWith(prefixes[index], prefixes[other]) && !ByteArrayUtil.startsWith(prefixes[other], prefixes[index])) { "All source/destination metadata/content namespaces must be disjoint" }
        }
    }
    class Entry internal constructor(
        internal val owner: DirectoryTreeMigration,
        path: List<String>,
        private val storedLayer: ByteArray,
        children: List<List<String>>,
        internal val prefix: ByteArray?,
    ) {
        private val storedPath = path.toList()
        private val storedChildren = children.map { it.toList() }
        val path: List<String> get() = storedPath.toList()
        val children: List<List<String>> get() = storedChildren.map { it.toList() }
        val layer: ByteArray get() = storedLayer.copyOf()
        val isPartition: Boolean get() = storedLayer.contentEquals(DirectoryLayer.PARTITION_LAYER)
    }
    class Progress internal constructor(val copiedKeys: Int, suffix: ByteArray?, val complete: Boolean) {
        private val suffix = suffix?.copyOf()
        val nextSuffix: ByteArray? get() = suffix?.copyOf()
    }
    /** Validate the complete reviewed inventory before writing a persistent migration owner marker.
     * Destination namespaces must initially be empty. Replaying prepare with the same configuration
     * permits restart after committed batches; no source key is modified.
     */
    suspend fun prepare(transaction: Transaction, inventory: List<Entry>) {
        require(inventory.all { it.owner === this })
        val byPath = inventory.associateBy { it.path }
        require(byPath.size == inventory.size) { "Duplicate directory in inventory" }
        val pending = inspectRoots(transaction).toMutableList()
        val seen = mutableSetOf<List<String>>()
        while (pending.isNotEmpty()) {
            val path = pending.removeAt(pending.lastIndex)
            check(seen.add(path)) { "Invalid directory inventory" }
            val entry = byPath[path] ?: error("Inventory is incomplete; inspect every named directory and partition child")
            if (entry.isPartition) requireNoPartitionRootData(transaction, entry)
            pending.addAll(entry.children)
        }
        check(seen.size == inventory.size) { "Inventory contains directories outside the source tree" }
        val marker = transaction.get(markerKey).await()
        if (marker != null) { check(marker.contentEquals(markerValue)) { "Destination belongs to another migration" }; return }
        for (prefix in listOf(destinationNode, destinationContent)) {
            val range = Range.startsWith(prefix)
            check(transaction.getRange(range.begin, range.end, 1).asList().await().isEmpty()) { "Destination migration namespace must initially be empty" }
        }
        transaction.set(markerKey, markerValue)
    }
    /**
     * Offline runner for a complete, caller-reviewed inventory. Every copy batch commits separately;
     * return only after all destination entries and root names verify. Writers must remain stopped.
     * Rebuild the inventory and rerun after interruption: committed copies are replayed safely from
     * the beginning. Use [copyBatch] directly when durable per-entry cursors are needed instead.
     * The source is retained; changing application configuration remains an explicit cutover step.
     */
    suspend fun copyAndVerify(database: Database, inventory: List<Entry>, keyLimit: Int = 100, byteLimit: Int = 1_000_000) {
        require(keyLimit in 1..1000 && byteLimit in 1..4_000_000)
        require(destinationLayout == DirectoryLayout.JAVA_CANONICAL) { "Runner requires a canonical destination" }
        val reviewed = inventory.sortedBy { it.path.size }
        database.runSuspend { prepare(it, reviewed) }
        for (entry in reviewed) {
            var suffix: ByteArray? = null
            do {
                val progress = database.runSuspend { copyBatch(it, entry, suffix, keyLimit, byteLimit) }
                suffix = progress.nextSuffix
            } while (!progress.complete)
        }
        for (entry in reviewed) {
            var suffix: ByteArray? = null
            do {
                val progress = database.readSuspend { verifyBatch(it, entry, suffix, keyLimit) }
                suffix = progress.nextSuffix
            } while (!progress.complete)
        }
        database.readSuspend { verifyRoots(it) }
    }

    private suspend fun checkPrepared(transaction: ReadTransaction) {
        check(transaction.get(markerKey).await()?.contentEquals(markerValue) == true) { "Prepare the complete reviewed inventory before copying" }
    }
    suspend fun inspectRoots(transaction: ReadTransaction): List<List<String>> = source.list(transaction).await().map { listOf(it) }
    suspend fun verifyRoots(transaction: ReadTransaction) {
        check(source.list(transaction).await().sorted() == destination.list(transaction).await().sorted()) { "Root directory list mismatch" }
    }
    suspend fun inspect(transaction: ReadTransaction, path: List<String>): Entry {
        require(path.isNotEmpty()) { "Inspect named directories under the source root" }
        val directory = source.open(transaction, path).await()
        val partition = directory.layer.contentEquals(DirectoryLayer.PARTITION_LAYER)
        val prefix = allocatedPrefix(transaction, path)
        if (!partition) check(prefix.contentEquals(directory.getKey())) { "Directory metadata/prefix mismatch" }
        check(ByteArrayUtil.startsWith(prefix, sourceContent)) { "Source directory escapes configured content namespace" }
        for (target in listOf(destinationNode, destinationContent)) {
            check(!ByteArrayUtil.startsWith(prefix, target) && !ByteArrayUtil.startsWith(target, prefix)) { "Destination overlaps an allocated source directory/partition" }
        }
        val children = source.list(transaction, path).await().map { path.toList() + it }
        return Entry(this, path.toList(), directory.layer.copyOf(), children, prefix?.copyOf())
    }
    private suspend fun allocatedPrefix(transaction: ReadTransaction, path: List<String>): ByteArray {
        var nodes = Subspace(sourceNode)
        var parent = nodes.get(nodes.getKey())
        var prefix = byteArrayOf()
        for (index in path.indices) {
            prefix = transaction.get(parent.get(0).pack(path[index])).await() ?: error("Directory pointer disappeared")
            val node = nodes.get(prefix)
            val layer = transaction.get(node.pack("layer".encodeToByteArray())).await()
            if (index < path.lastIndex && layer?.contentEquals(DirectoryLayer.PARTITION_LAYER) == true && sourceLayout != DirectoryLayout.LEGACY_NATIVE_FLAT_PARTITIONS) {
                nodes = if (sourceLayout == DirectoryLayout.JAVA_CANONICAL) Subspace(prefix + byteArrayOf(0xfe.toByte())) else nodes.get(prefix)
                parent = nodes.get(nodes.getKey())
            } else parent = node
        }
        return prefix
    }

    /** Released Native partition roots could be packed as ordinary subspaces.
     * Their direct data has no canonical directory destination. Fail closed, but
     * skip canonical metadata and data owned by known children, including manual prefixes.
     * Work is bounded by child namespaces rather than the number of child records.
     */
    private suspend fun requireNoPartitionRootData(transaction: ReadTransaction, entry: Entry) {
        val prefix = allocatedPrefix(transaction, entry.path)
        check(prefix.contentEquals(entry.prefix)) { "Source partition prefix changed since inspection" }
        val childPrefixes = source.list(transaction, entry.path).await().map { child ->
            allocatedPrefix(transaction, entry.path + child)
        }.filter { ByteArrayUtil.startsWith(it, prefix) }
        val ownedPrefixes = childPrefixes + if (sourceLayout == DirectoryLayout.JAVA_CANONICAL) {
            // Canonical partition metadata is inside its raw data prefix.
            listOf(prefix + byteArrayOf(0xfe.toByte()))
        } else emptyList()
        val end = Range.startsWith(prefix).end
        var begin = prefix
        while (ByteArrayUtil.compareUnsigned(begin, end) < 0) {
            val row = transaction.getRange(begin, end, 1).asList().await().firstOrNull() ?: return
            val owner = ownedPrefixes.firstOrNull { ByteArrayUtil.startsWith(row.key, it) }
            check(owner != null) {
                "Partition /${entry.path.joinToString("/")} contains direct user data; " +
                    "move it into an ordinary directory before migrating this tree"
            }
            begin = Range.startsWith(owner).end
        }
    }

    suspend fun copyBatch(transaction: Transaction, entry: Entry, afterSuffix: ByteArray? = null, keyLimit: Int = 100, byteLimit: Int = 1_000_000): Progress {
        require(entry.owner === this)
        checkPrepared(transaction)
        require(keyLimit in 1..1000 && byteLimit in 1..4_000_000)
        val current = source.open(transaction, entry.path).await()
        check(current.layer.contentEquals(entry.layer)) { "Source layer changed since inspection" }
        if (entry.isPartition) requireNoPartitionRootData(transaction, entry)
        else check(current.getKey().contentEquals(entry.prefix!!)) { "Source prefix changed since inspection" }
        val target = destination.createOrOpen(transaction, entry.path, entry.layer).await()
        check(target.layer.contentEquals(entry.layer)) { "Destination layer differs from inspected source" }
        check(source.list(transaction, entry.path).await().toSet() == entry.children.map { it.last() }.toSet()) { "Source children changed since inspection" }
        if (entry.isPartition) return Progress(0, null, true)
        val targetPrefix = target.getKey()
        check(ByteArrayUtil.startsWith(targetPrefix, destinationContent)) { "Destination directory escapes configured content namespace" }
        val prefix = entry.prefix!!
        val begin = if (afterSuffix == null) prefix else prefix + afterSuffix + byteArrayOf(0)
        val iterator = transaction.getRange(begin, Range.startsWith(prefix).end, keyLimit + 1).iterator()
        val rows = mutableListOf<KeyValue>()
        var bytes = 0L
        var more = false
        try {
            while (iterator.onHasNext().await()) {
                val row = iterator.next()
                val cost = row.key.size.toLong() + row.value.size + targetPrefix.size
                require(cost <= byteLimit) { "Single key exceeds migration byte limit" }
                if (rows.size == keyLimit || bytes + cost > byteLimit) { more = true; break }
                rows += row
                bytes += cost
            }
        } finally { iterator.cancel() }
        val targets = rows.map { targetPrefix + it.key.copyOfRange(prefix.size, it.key.size) }
        for (index in rows.indices) {
            val existing = transaction.get(targets[index]).await()
            check(existing == null || existing.contentEquals(rows[index].value)) { "Destination data collision" }
        }
        for (index in rows.indices) transaction.set(targets[index], rows[index].value)
        return Progress(rows.size, rows.lastOrNull()?.key?.copyOfRange(prefix.size, rows.last().key.size) ?: afterSuffix, !more)
    }
    /** Compare named directory bytes; partition namespaces must contain only metadata/child data. */
    suspend fun verifyBatch(transaction: ReadTransaction, entry: Entry, afterSuffix: ByteArray? = null, keyLimit: Int = 100): Progress {
        require(entry.owner === this && keyLimit in 1..1000)
        checkPrepared(transaction)
        val from = source.open(transaction, entry.path).await()
        val to = destination.open(transaction, entry.path).await()
        check(from.layer.contentEquals(entry.layer) && from.layer.contentEquals(to.layer)) { "Layer mismatch" }
        if (entry.isPartition) requireNoPartitionRootData(transaction, entry)
        else {
            check(from.getKey().contentEquals(entry.prefix!!)) { "Source prefix changed since inspection" }
            check(ByteArrayUtil.startsWith(to.getKey(), destinationContent)) { "Destination escapes configured namespace" }
        }
        check(source.list(transaction, entry.path).await().sorted() == destination.list(transaction, entry.path).await().sorted()) { "Child directory mismatch" }
        if (entry.isPartition) return Progress(0, null, true)
        val sourcePrefix = from.getKey()
        val targetPrefix = to.getKey()
        val sourceRows = transaction.getRange(if (afterSuffix == null) sourcePrefix else sourcePrefix + afterSuffix + byteArrayOf(0), Range.startsWith(sourcePrefix).end, keyLimit + 1).asList().await()
        val targetRows = transaction.getRange(if (afterSuffix == null) targetPrefix else targetPrefix + afterSuffix + byteArrayOf(0), Range.startsWith(targetPrefix).end, keyLimit + 1).asList().await()
        check(sourceRows.size == targetRows.size) { "Key count mismatch" }
        for (index in sourceRows.indices) {
            check(sourceRows[index].key.copyOfRange(sourcePrefix.size, sourceRows[index].key.size).contentEquals(targetRows[index].key.copyOfRange(targetPrefix.size, targetRows[index].key.size)) && sourceRows[index].value.contentEquals(targetRows[index].value)) { "Key/value mismatch" }
        }
        val compared = sourceRows.take(keyLimit)
        return Progress(compared.size, compared.lastOrNull()?.key?.copyOfRange(sourcePrefix.size, compared.last().key.size) ?: afterSuffix, sourceRows.size <= keyLimit)
    }
}
