package maryk.foundationdb.jvm

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.stream.Stream
import com.apple.foundationdb.EventKeeper as JavaEventKeeper
import com.apple.foundationdb.Database as JavaDatabase
import com.apple.foundationdb.Transaction as JavaTransaction
import com.apple.foundationdb.ReadTransaction as JavaReadTransaction
import com.apple.foundationdb.Tenant as JavaTenant
import com.apple.foundationdb.FDB as JavaFDB
import maryk.foundationdb.Database
import maryk.foundationdb.Event
import maryk.foundationdb.EventKeeper
import maryk.foundationdb.FDB
import maryk.foundationdb.FdbEvent
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.ReadTransaction
import maryk.foundationdb.Transaction
import maryk.foundationdb.Tenant
import maryk.foundationdb.asCompletableFuture
import maryk.foundationdb.toFdbFuture
import maryk.foundationdb.tuple.Tuple

/** Live Java objects share ownership with their wrappers: closing either closes the same resource. */
fun Database.asJavaDatabase(): JavaDatabase = delegate
fun Transaction.asJavaTransaction(): JavaTransaction = delegate
fun ReadTransaction.asJavaReadTransaction(): JavaReadTransaction = delegate
fun Tenant.asJavaTenant(): JavaTenant = delegate
fun FDB.asJavaFDB(): JavaFDB = delegate

/** Cancelling this bridge requests cancellation of the source operation. */
fun <T> FdbFuture<T>.toCompletableFuture(): CompletableFuture<T> = asCompletableFuture()
fun <T> CompletableFuture<T>.toFoundationDbFuture(): FdbFuture<T> = toFdbFuture()

fun FDB.open(clusterFilePath: String?, executor: Executor, events: EventKeeper? = null): Database =
    Database(delegate.open(clusterFilePath, executor, events?.asJavaEventKeeper()))
fun FDB.open(clusterFilePath: String?, events: EventKeeper): Database = Database(delegate.open(clusterFilePath, events.asJavaEventKeeper()))
fun FDB.startNetwork(executor: Executor) = delegate.startNetwork(executor)
fun FDB.disableShutdownHook() = delegate.disableShutdownHook()
fun FDB.enableDirectBufferQuery(enabled: Boolean) = delegate.enableDirectBufferQuery(enabled)
fun FDB.isDirectBufferQueriesEnabled(): Boolean = delegate.isDirectBufferQueriesEnabled
fun FDB.resizeDirectBufferPool(poolSize: Int, bufferSize: Int) = delegate.resizeDirectBufferPool(poolSize, bufferSize)
fun Database.createTransaction(executor: Executor, events: EventKeeper? = null): Transaction =
    Transaction(if (events == null) delegate.createTransaction(executor) else delegate.createTransaction(executor, events.asJavaEventKeeper()))
fun Tenant.createTransaction(executor: Executor): Transaction = Transaction(delegate.createTransaction(executor))
val Database.executor: Executor get() = delegate.executor
val ReadTransaction.executor: Executor get() = delegate.executor
val Tenant.executor: Executor get() = delegate.executor
fun Tuple.packInto(buffer: ByteBuffer) = delegate.packInto(buffer)
/** Stream values use the common Kotlin representation, including TupleInteger and Uuid. */
fun Tuple.stream(): Stream<Any?> = items.stream()
fun Tuple.Companion.fromStream(stream: Stream<*>): Tuple = fromItems(stream.iterator().asSequence().asIterable())

private data class ImportedEvent(val event: JavaEventKeeper.Event) : Event {
    override val name: String get() = event.name()
    override val isTimeEvent: Boolean get() = event.isTimeEvent
}
private data class ExportedEvent(val event: Event) : JavaEventKeeper.Event {
    override fun name(): String = event.name
    override fun isTimeEvent(): Boolean = event.isTimeEvent
}
fun Event.asJavaEvent(): JavaEventKeeper.Event = when (this) {
    is FdbEvent -> JavaEventKeeper.Events.valueOf(name)
    is ImportedEvent -> event
    else -> ExportedEvent(this)
}
fun JavaEventKeeper.Event.asCommonEvent(): Event = when (this) {
    is JavaEventKeeper.Events -> FdbEvent.entries.firstOrNull { it.name == name() } ?: ImportedEvent(this)
    is ExportedEvent -> event
    else -> ImportedEvent(this)
}
fun EventKeeper.asJavaEventKeeper(): JavaEventKeeper = object : JavaEventKeeper {
    override fun count(event: JavaEventKeeper.Event, amt: Long) = this@asJavaEventKeeper.count(event.asCommonEvent(), amt)
    override fun timeNanos(event: JavaEventKeeper.Event, nanos: Long) = this@asJavaEventKeeper.timeNanos(event.asCommonEvent(), nanos)
    override fun getCount(event: JavaEventKeeper.Event): Long = this@asJavaEventKeeper.getCount(event.asCommonEvent())
    override fun getTimeNanos(event: JavaEventKeeper.Event): Long = this@asJavaEventKeeper.getTimeNanos(event.asCommonEvent())
}
