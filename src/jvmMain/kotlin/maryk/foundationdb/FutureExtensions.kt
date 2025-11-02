package maryk.foundationdb

import java.util.concurrent.CompletableFuture

fun <T> CompletableFuture<T>.asFdbFuture(): FdbFuture<T> = this.toFdbFuture()
