package maryk.foundationdb

import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertSame

class TransactionJvmExceptionUnwrapTest {
    @Test
    fun unwrapsCompletionAndExecutionWrappers() {
        val root = IllegalStateException("root")
        assertSame(root, CompletionException(root).unwrapCompletionLike())
        assertSame(root, ExecutionException(root).unwrapCompletionLike())
    }

    @Test
    fun leavesNonWrapperThrowablesUntouched() {
        val root = IllegalArgumentException("root")
        assertSame(root, root.unwrapCompletionLike())
    }
}
