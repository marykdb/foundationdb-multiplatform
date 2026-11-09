import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import maryk.foundationdb.IterableComparator
import maryk.foundationdb.KeyValue
import maryk.foundationdb.MappedKeyValue
import maryk.foundationdb.TenantManagement
import maryk.foundationdb.listTenantsFlow
import maryk.foundationdb.Database
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.FutureCancellation
import maryk.foundationdb.asDeferred
import maryk.foundationdb.asFdbFuture
import maryk.foundationdb.readSuspend
import maryk.foundationdb.runSuspend
import maryk.foundationdb.async.AsyncIterable
import maryk.foundationdb.async.asFlow
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.TupleInteger

fun results(source: AsyncIterable<Int>): Flow<Int> = source.asFlow()
fun observe(source: FdbFuture<Int>, scope: CoroutineScope): Deferred<Int> = source.asDeferred(scope)
fun adapt(source: Deferred<Int>): FdbFuture<Int> = source.asFdbFuture(FutureCancellation.OBSERVE)
suspend fun read(database: Database): ByteArray? = database.readSuspend(CoroutineName("consumer")) { it.get(Tuple.from(TupleInteger.parse("18446744073709551616")).pack()).await() }
suspend fun write(database: Database) = database.runSuspend { it.set(byteArrayOf(1), byteArrayOf(2)) }

fun compareTupleItems(a: Iterable<Any?>, b: Iterable<Any?>): Int = IterableComparator<Any?>().compare(a, b)
fun asKeyValue(row: MappedKeyValue): KeyValue = row
fun tenantStream(database: Database): Flow<KeyValue> = TenantManagement.listTenantsFlow(database)
