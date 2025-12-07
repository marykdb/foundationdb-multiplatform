package maryk.foundationdb.directory

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.foundationdb.collectRange
import maryk.foundationdb.nextKey
import maryk.foundationdb.subspace.Subspace
import maryk.foundationdb.tuple.Tuple
import kotlin.random.Random

/**
 * Port of FoundationDB's HighContentionAllocator used by the DirectoryLayer to
 * allocate unique, prefix-free directory prefixes in a contention-friendly way.
 */
internal class HighContentionAllocator(private val subspace: Subspace) {
    private val counters = subspace.get(0)
    private val recent = subspace.get(1)

    suspend fun allocate(transaction: Transaction): ByteArray {
        var start = 0L
        while (true) {
            // Find the current window start (lowest counter present)
            val countersRange = transaction.collectRange(
                counters.range().begin,
                counters.range().end,
                1,
                reverse = false
            ).await()
            if (countersRange.values.isNotEmpty()) {
                start = counters.unpack(countersRange.values[0].key).getLong(0)
            }

            var windowAdvanced = false
            while (true) {
                if (windowAdvanced) {
                    transaction.clear(Range(counters.pack(), counters.get(start).pack()))
                    transaction.clear(Range(recent.pack(), recent.get(start).pack()))
                }

                val inc = ONE
                val counterKey = counters.get(start).pack()
                transaction.atomicAdd(counterKey, inc)
                val countBytes = transaction.get(counterKey).await()
                val count = countBytes?.toLong() ?: 0L

                val window = windowSize(start)
                if (count * 2 < window) {
                    // Candidate window is sufficiently empty
                    val prefix = pickCandidate(transaction, start, window)
                    if (prefix != null) return Tuple.from(prefix).pack()
                    // otherwise window moved, restart outer loop
                    break
                }

                start += window
                windowAdvanced = true
            }
        }
    }

    private suspend fun pickCandidate(transaction: Transaction, start: Long, window: Long): Long? {
        while (true) {
            val candidate = Random.Default.nextLong(start, start + window)
            val latestCounter = transaction.collectRange(
                counters.range().begin,
                counters.range().end,
                1,
                reverse = false
            ).await()
            val candidateKey = recent.get(candidate).pack()
            val candidateValue = transaction.get(candidateKey).await()

            // Reserve the candidate in the recent subspace; mimic NEXT_WRITE_NO_WRITE_CONFLICT_RANGE
            transaction.set(candidateKey, ByteArray(0))

            val currentWindowStart = if (latestCounter.values.isNotEmpty()) {
                counters.unpack(latestCounter.values[0].key).getLong(0)
            } else {
                0L
            }

            if (currentWindowStart > start) {
                // Window advanced; caller should restart with new start
                return null
            }

            if (candidateValue == null) {
                transaction.addWriteConflictKey(candidateKey)
                return candidate
            }
        }
    }

    private fun windowSize(start: Long): Long = when {
        start < 255 -> 64
        start < 65_535 -> 1024
        else -> 8192
    }

    companion object {
        private val ONE = byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0)

        private fun ByteArray.toLong(): Long {
            require(size == 8) { "Invalid counter size" }
            var result = 0L
            for (i in 0 until 8) {
                result = result or ((this[i].toLong() and 0xFF) shl (8 * i))
            }
            return result
        }
    }
}
