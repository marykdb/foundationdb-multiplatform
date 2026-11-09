# Portable API guide

The common API exposes FoundationDB client capabilities with Kotlin types. It follows
supported Java client behavior while providing coroutine-friendly futures and iteration.
JVM-specific integration is available through `maryk.foundationdb.jvm`.

## Transactions and cancellation

Use `runSuspend` and `readSuspend` for suspending transaction blocks. Each retry attempt
runs as a supervised child of the caller. Context overloads can override dispatcher,
name and other context elements; Job overrides are rejected to preserve cancellation
ownership. Retry blocks must be safe to execute more than once. These overloads control
the user block, not FoundationDB's network thread or every Java callback.

`FdbFuture.await()` owns cancellation: cancelling the waiting coroutine requests
cancellation of the source operation. `awaitObserved()` cancels only the wait.
`FdbFuture.asDeferred(scope)` and `Deferred.asFdbFuture()` default to
`FutureCancellation.OBSERVE`. Choose `PROPAGATE` when the adapter owns its source.
Adapting a lazy Deferred starts it. Ordinary exceptional completion is distinct from
future cancellation. Custom FdbFuture implementations must implement `awaitObserved`.

`AsyncUtil.getAll` and `whenAll` wait for all inputs, including exceptional completions.
`whenAny` waits for one; an empty collection remains pending until cancelled. Completion
and handler helpers observe their input and handler-returned futures without cancelling
them when their result is cancelled. Cancel input operations explicitly when you own
them. `whileTrue` owns the operation produced by each invocation of its body.

Cancellation is best effort. In particular, cancellation of an administration operation
does not guarantee that its transaction did not commit.

## Streaming and collection

`AsyncIterable.asFlow()` is cold and backpressured: each collector obtains a fresh
iterator. Completion, failure and cancellation release it. Collect within the originating
transaction's lifetime; a Flow does not extend that lifetime or retry partially emitted
results. Collection helpers acquire their iterator eagerly and own its cleanup.
If cleanup also fails, an existing read, consumer or cancellation failure remains primary.
Only one consumer should advance a cursor.

`TenantManagement.openTenantsIterator` owns a transaction and closes it on exhaustion
or read failure. Close or cancel it after early termination. `listTenantsFlow` creates a
fresh iterator and transaction per collector. Materialized `listTenants` can retry its
whole read; a partially emitted tenant stream does not retry automatically. Resume a
stream from application-owned progress after explicitly reopening it.

Plain, mapped and tenant range limits must be nonnegative. Zero means unlimited.
Finite range summaries distinguish exhaustion from additional available rows and retain
the last returned key, including when the terminal page is empty.

Locality boundary iterators own a separate transaction, including when created from a
caller transaction. They do not change the caller's options or require it to remain open.
An already available successful read version is copied. Native boundary reads retry
retryable errors and resume after the last emitted boundary. Close the iterator after
early termination; materialized boundary reads manage cleanup automatically.

## Values and key utilities

`TupleInteger` is an immutable exact integer representation with comparison, decimal
parsing, magnitude bytes and numeric conversions. Arithmetic is excluded. Tuple encoding
supports magnitudes up to 255 bytes. `toLongExact` rejects overflow; Number conversions
retain Java truncation semantics. JVM extensions convert to and from `java.math.BigInteger`.
No third-party arithmetic dependency is required. Versionstamp offsets use two bytes
before API 520 and four bytes otherwise.

`IterableComparator` follows FoundationDB tuple ordering for supported values, including
UTF-8 strings, bytes, nulls, numeric values and nested tuples. It stops at the first
difference. Arbitrary application-specific Comparable objects are outside the tuple domain.

`MappedKeyValue` extends `KeyValue` and retains its data-class constructor, copy and
component methods. Equality compares byte arrays by content and includes secondary range
results. Plain and mapped rows are unequal in both directions. Public Native KeyValue
construction copies its inputs; hash values are not a persisted storage format.

Byte-array replacement matches only within the selected source slice, uses non-overlapping
matches and does not mutate inputs. Invalid slices and oversized results are rejected.
Null sources return null; null replacements delete matching bytes; null or empty patterns
return a copy of the selected slice.

Single-key conflict methods cover only the exact key, including an empty user key.
To fence an entire namespace, explicitly add a conflict range using
`Range.startsWith(prefix)`. Snapshot conditional read-conflict methods are no-ops.

## JVM integration and events

`maryk.foundationdb.jvm` provides executor/event-keeper factories, runtime controls,
ByteBuffer/Stream helpers, CompletableFuture bridges and access to live official Java
objects. Wrappers and Java objects share close ownership. Use the common retry API:
calling Java `onError` directly transfers ownership outside the wrapper and invalidates
its Java transaction view. Future bridges forward explicit cancellation.
Deprecated Cluster APIs and server workload/testing internals are outside the common API.

`Event` supports custom events; `FdbEvent` supplies predefined events. `MapEventKeeper`
is thread-safe. Native records explicitly submitted events and does not synthesize
Java/JNI/direct-buffer metrics. JVM adapters forward Java instrumentation to the common
keeper. Enable instrumentation explicitly when opening a JVM database or transaction.

## Upgrade compatibility

Directory and asynchronous iteration contracts are implementable interfaces.
`DirectorySubspace` is both a `Subspace` and a `Directory`; partition roots reject
namespace operations. Existing consumers need recompilation for these hierarchy changes.

Native mixed-prefix subspaces and old directory partitions require explicit upgrade
handling. Canonical Native access to recognizable old partition metadata throws
`DirectoryMigrationRequiredException`, a `DirectoryVersionException` subtype. There is
no equivalent JVM detector or reliable inference for arbitrary raw keys. Read the
[short Native upgrade guide](native-layout-upgrade.md) and
[migration details](migrations.md) before opening affected data. The offline migration
runner verifies copied data, retains the source and leaves application cutover explicit.

## Development checks

`checkKotlinAbi` checks JVM and Native API baselines in `api/`. Use `updateKotlinAbi`
only for reviewed API changes. These baselines do not make earlier breaking changes
binary compatible.

`python3 verification/check-external-consumer.py` compiles independent Gradle consumers
against generated artifacts in a temporary filesystem Maven repository. It checks the
root and JVM coordinates, plus JVM POM-only resolution, without project substitution
or explicitly adding the exposed coroutine/Java dependencies. It publishes nothing remotely.

The optional Native counter benchmark runs with
`-PfoundationDbNativeTestFilter=maryk.foundationdb.MetricsBenchmarkNativeTest.*`.
Timing results are diagnostic rather than performance assertions.
