package maryk.foundationdb

import com.apple.foundationdb.NetworkOptions as JavaNetworkOptions
import com.apple.foundationdb.DatabaseOptions as JavaDatabaseOptions
import com.apple.foundationdb.TransactionOptions as JavaTransactionOptions
import com.apple.foundationdb.MutationType as JavaMutationType
import com.apple.foundationdb.ReadTransaction as JavaReadTransaction
import com.apple.foundationdb.Database as JavaDatabase
import com.apple.foundationdb.Tenant as JavaTenant
import com.apple.foundationdb.Transaction as JavaTransaction
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiParityTest {
    @Test
    fun everyUpstreamOptionSetterHasAnIdenticalTypedWrapper() {
        for ((upstream, wrapper) in listOf(JavaNetworkOptions::class.java to NetworkOptions::class.java,
            JavaDatabaseOptions::class.java to DatabaseOptions::class.java,
            JavaTransactionOptions::class.java to TransactionOptions::class.java)) {
            val setters = upstream.declaredMethods.filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("set") }
            assertTrue(setters.isNotEmpty())
            for (method in setters) {
                val actual = wrapper.getMethod(method.name, *method.parameterTypes)
                assertEquals(method.returnType, actual.returnType, method.name)
            }
        }
    }

    @Test
    fun allMutationKindsAreRepresented() {
        val names = MutationType.entries.map { when (it) {
            MutationType.AND -> "BIT_AND"
            MutationType.OR -> "BIT_OR"
            MutationType.XOR -> "BIT_XOR"
            else -> it.name
        } }.toSet()
        // Upstream retains deprecated aliases AND/OR/XOR alongside BIT_*.
        assertEquals(JavaMutationType.values().toList().map { it.name }.filterNot { it in setOf("AND", "OR", "XOR") }.toSet(), names)
    }

    @Test
    fun sharedCoreOperationNamesMatchUpstream() {
        for ((upstream, wrapper, exclusions) in listOf(
            Triple(JavaReadTransaction::class.java, ReadTransaction::class.java, setOf("getExecutor")),
            Triple(JavaTransaction::class.java, Transaction::class.java, setOf("getExecutor")),
            Triple(JavaDatabase::class.java, Database::class.java, setOf("getExecutor")),
            Triple(JavaTenant::class.java, Tenant::class.java, setOf("getExecutor")))) {
            val expected = upstream.methods.map { it.name }.toSet() - exclusions
            val actual = wrapper.methods.map { it.name }.toSet()
            assertTrue((expected - actual).isEmpty(), "${wrapper.simpleName}: missing ${expected - actual}")
        }
    }
}
