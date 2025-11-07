package maryk.foundationdb

import kotlinx.coroutines.runBlocking
import maryk.foundationdb.tuple.Tuple
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class FoundationDbTestHarness {
    @OptIn(ExperimentalUuidApi::class)
    val namespace: String = "maryk-kmp-${Uuid.random()}"
    private val namespaceBytes = namespace.encodeToByteArray()

    val database: Database

    init {
        if (!FDB.isAPIVersionSelected()) {
            FDB.selectAPIVersion(ApiVersion.LATEST)
        }
        database = FDB.instance().open()
    }

    fun key(vararg segments: String): ByteArray =
        keyString(*segments).encodeToByteArray()

    fun keyTuple(vararg segments: String): Tuple =
        Tuple.from(*arrayOf(namespace) + segments)

    fun prefixBytes(vararg segments: String): ByteArray =
        if (segments.isEmpty()) namespaceBytes else keyBytesForSegments(*segments)

    suspend fun clearNamespace() {
        database.runSuspend { txn ->
            txn.clear(Range.startsWith(namespaceBytes))
        }
    }

    fun runAndReset(block: suspend FoundationDbTestHarness.() -> Unit) = runBlocking {
        try {
            block()
        } finally {
            clearNamespace()
        }
    }

    private fun keyString(vararg segments: String): String =
        buildString {
            append(namespace)
            for (segment in segments) {
                append('/')
                append(segment)
            }
        }

    private fun keyBytesForSegments(vararg segments: String): ByteArray =
        keyString(*segments).encodeToByteArray()
}

internal fun ByteArray.decodeToUtf8(): String = decodeToString()
