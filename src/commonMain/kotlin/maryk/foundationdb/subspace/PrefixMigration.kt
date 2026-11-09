package maryk.foundationdb.subspace

import maryk.foundationdb.ByteArrayUtil
import maryk.foundationdb.KeyValue
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.Range

/**
 * Explicit offline prefix migration. Stop every writer to BOTH namespaces for the entire migration.
 * This moves raw keys/values only: embedded directory pointers require a directory-aware migration.
 * Run inspect in a read transaction, review the batch, then apply in a write transaction and commit.
 * Repeatedly inspect from the beginning until empty; completed source keys disappear atomically.
 */
class PrefixMigration(source: Subspace, destination: Subspace) {
    private val source = source.getKey().copyOf()
    private val destination = destination.getKey().copyOf()
    init {
        require(this.source.isNotEmpty() && this.destination.isNotEmpty())
        require(!ByteArrayUtil.startsWith(this.source, this.destination) && !ByteArrayUtil.startsWith(this.destination, this.source)) { "Migration prefixes must be disjoint" }
    }
    class Batch internal constructor(internal val owner: PrefixMigration, internal val rows: List<KeyValue>) {
        val keyCount: Int get() = rows.size
        val byteCount: Long get() = rows.sumOf { it.key.size.toLong() + it.value.size }
        /** Copies for review; mutating them cannot change the pending migration. */
        fun entries(): List<KeyValue> = rows.map { KeyValue(it.key.copyOf(), it.value.copyOf()) }
    }
    suspend fun inspect(transaction: ReadTransaction, keyLimit: Int = 100, byteLimit: Int = 1_000_000): Batch {
        require(keyLimit in 1..1000 && byteLimit in 1..4_000_000)
        val iterator = transaction.getRange(Range.startsWith(source).begin, Range.startsWith(source).end, keyLimit).iterator()
        val rows = mutableListOf<KeyValue>()
        var bytes = 0L
        try {
            while (iterator.onHasNext().await()) {
                val row = iterator.next()
                val cost = row.key.size.toLong() + row.value.size + destination.size
                require(cost <= byteLimit) { "Single key exceeds migration byte limit" }
                if (bytes + cost > byteLimit) break
                rows += KeyValue(row.key.copyOf(), row.value.copyOf())
                bytes += cost
            }
        } finally { iterator.cancel() }
        return Batch(this, rows)
    }
    suspend fun apply(transaction: Transaction, batch: Batch): Int {
        require(batch.owner === this) { "Batch belongs to another migration" }
        val pending = mutableListOf<Pair<KeyValue, ByteArray>>()
        // Validate every read before staging any mutations; ordinary reads establish conflicts.
        for (row in batch.rows) {
            val target = destination + row.key.copyOfRange(source.size, row.key.size)
            val current = transaction.get(row.key).await()
            val existing = transaction.get(target).await()
            if (current == null && existing != null && existing.contentEquals(row.value)) continue
            check(current != null && current.contentEquals(row.value)) { "Source changed since inspection" }
            check(existing == null) { "Destination collision; no migration mutations staged" }
            pending += row to target
        }
        for ((row, target) in pending) { transaction.set(target, row.value); transaction.clear(row.key) }
        return pending.size
    }
}
