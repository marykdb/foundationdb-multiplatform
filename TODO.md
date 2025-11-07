# FoundationDB Multiplatform TODO

This repository now exposes the core FoundationDB client surface as Kotlin Multiplatform `expect` APIs with JVM and native `actual` implementations. The native target now covers the same surface area as JVM, including async utilities, directory helpers, tuple/subspace helpers, and coroutine-friendly futures.

## Remaining Work

- **Maintenance cleanup** (ongoing)  
  - Keep documenting new coverage as API surface expands, and track any new helpers (watchers, versionstamp utilities) that surface later.

## Implementation Matrix (per FDB Java API)

Legend: ✅ Implemented, ⬜ Pending, ➖ Not currently planned / defer

### Core API (`com.apple.foundationdb`)

| Class / Component         | JVM | Native | Tested | Notes |
| ------------------------- | --- | ------ | ------ | ----- |
| `FDB`                     | ✅  | ✅ | ✅ | entry point `selectAPIVersion`, `open` |
| `Database`                | ✅  | ✅ | ✅ | wrapped in `maryk.foundationdb.Database` |
| `Tenant`                  | ✅  | ✅ | ✅ | tenant context support |
| `Transaction`             | ✅  | ✅ | ✅ | includes mutation helpers |
| `ReadTransaction`         | ✅  | ✅ | ✅ | snapshot + read operations |
| `TransactionContext`      | ✅  | ✅ | ✅ | surfaced via shared interface |
| `ReadTransactionContext`  | ✅  | ✅ | ✅ | shared interface available via TransactionContext |
| `DatabaseOptions`         | ✅  | ✅ | ✅ | extended txn defaults + option helper DSL |
| `NetworkOptions`          | ✅  | ✅ | ✅ | broad trace/TLS/buggify knobs exposed |
| `TransactionOptions`      | ✅  | ✅ | ✅ | exposes major knobs + option helper DSL |
| `TenantManagement`        | ✅  | ✅ | ✅ | create/delete/list tenants bridged |
| `Cluster` / `ClusterOptions` | ✅  | ✅ | ✅ | wrapper available for connect-close flows |
| `EventKeeper` / `MapEventKeeper` | ✅ | ✅ | ✅ | basic event instrumentation available |
| `LocalityUtil`            | ✅  | ✅ | ✅ | boundary key + address helpers exposed |
| `Range`, `KeySelector`, `KeyValue` | ✅ | ✅ | ✅ | basic key-space primitives |
| `MutationType`            | ✅  | ✅ | ✅ | enum mapping for `mutate` |
| `StreamingMode`           | ✅  | ✅ | ✅ | enum bridged for range read batching hints |
| `RangeQuery` / `RangeResult*` | ✅ | ✅ | ✅ | use `ReadTransaction.collectRange` + summaries |
| `Future*` specializations (`FutureResult`, `FutureVoid`, etc.) | ✅ | ✅ | ✅ | use existing `CompletableFuture.toFdbFuture` bridge via `NativeFuture` |
| `NativeFuture` / `NativeObjectWrapper` | ➖ | ➖ | ➖ | internal JNI plumbing, usually hidden |
| `ApiVersion`, `ConflictRangeType` | ✅ | ✅ | ✅ | constants exposed in Kotlin API |

### Async Utilities (`com.apple.foundationdb.async`)

| Class / Component    | JVM | Native | Tested | Notes |
| -------------------- | --- | ------ | ------ | ----- |
| `AsyncIterable`      | ✅  | ✅ | ✅ | wrapped with mapper-based bridge |
| `AsyncIterator`      | ✅  | ✅ | ✅ | coroutine-friendly `next()` |
| `AsyncUtil`          | ✅  | ✅ | ✅ | helpers to collect / iterate async streams |
| `CloneableException` | ➖ | ➖ | ➖ | currently unused directly |
| `Cancellable` / `CloseableAsyncIterator` | ✅ | ✅ | ✅ | mapped to FDB async interfaces |

### Directory Layer (`com.apple.foundationdb.directory`)

| Class / Component      | JVM | Native | Tested | Notes |
| ---------------------- | --- | ------ | ------ | ----- |
| `DirectoryLayer`       | ✅  | ✅ | ✅ | create/open directory paths |
| `DirectorySubspace`    | ✅  | ✅ | ✅ | pack/unpack directory prefixes |
| `DirectoryPartition`   | ✅  | ✅ | ✅ | typealias for partition subspaces |
| `Directory` (interface) | ✅ | ✅ | ✅ | wrapper for directory operations |
| Exceptions (`Directory*Exception`) | ✅ | ✅ | ✅ | shared Kotlin implementations with parity tests |
| Utility classes (`PathUtil`, `DirectoryUtil`) | ✅ | ✅ | ✅ | helpers exposed in common code with coverage |

### Tuple & Subspace (`com.apple.foundationdb.tuple` / `subspace`)

| Class / Component | JVM | Native | Tested | Notes |
| ----------------- | --- | ------ | ------ | ----- |
| `Tuple`           | ✅  | ✅ | ✅ | packing/unpacking only |
| `Versionstamp`    | ✅  | ✅ | ✅ | supports packing/unpacking of versionstamp tuples |
| `ByteArrayUtil`        | ✅  | ✅ | ✅ | helper utilities exposed in common/jvm/native |
| `FastByteComparisons`  | ✅  | ✅ | ✅ | comparator + byte ordering helpers |
| `IterableComparator`   | ✅  | ✅ | ✅ | matches Java tuple comparator support |
| `TupleUtil`            | ✅  | ✅ | ✅ | numeric helpers and encoding utilities |
| `StringUtil`           | ✅  | ✅ | ✅ | UTF utilities used by tuple encoding |
| `Subspace`        | ✅  | ✅ | ✅ | prefix helper for tuple-derived key ranges |

### Testing & Misc (`com.apple.foundationdb.testing`, etc.)

| Class / Component | JVM | Native | Tested | Notes |
| ----------------- | --- | ------ | ------ | ----- |
| Workload/test harness classes | ➖ | ➖ | ➖ | likely out of scope for production API |

### Project-specific Glue

| Component                        | JVM | Native | Tested | Notes |
| -------------------------------- | --- | ------ | ------ | ----- |
| `FdbFuture` abstraction          | ✅  | ✅ | ✅ | unify async semantics |
| Coroutine helpers (`runSuspend`) | ✅  | ✅ | ✅ | leverage native event loops |

✅ — Implemented and wired for both JVM & native  
➖ — Currently deferred / optional

### Remaining Java-only pieces

The upstream `org.foundationdb:fdb-java` bundle still exposes several JVM-only helpers that have not yet been mirrored in the common API. They include:

- `NativeFuture`, `NativeObjectWrapper`, `JNIUtil` and the assorted `Future*` / `Native*` plumbing (already implicit in our `NativeFuture` but not one-to-one); 
- direct buffer helpers (`DirectBufferIterator`, `DirectBufferPool`) used by the Java runtime;
- tuple utilities (additional encoding helpers) if new tuple features require them;
- `OptionsSet`, `OptionConsumer` and the various deprecated `set*` helpers the Java client exposes via `Options` objects;
- directory-specific exceptions such as `DirectoryAlreadyExistsException`, `DirectoryMoveException`, `DirectoryVersionException`, `MismatchedLayerException`, `NoSuchDirectoryException`, plus helpers like `DirectoryUtil`/`PathUtil`;
- testing helpers under `com.apple.foundationdb.testing` (workloads, `PerfMetric`, `Promise`, etc.).

These can stay deferred unless we need them for additional functionality or tighter parity; keep the list here so we can revisit when those APIs are required.

### High-priority native gaps to consider

The sections above include everything we deliberately kept in TODO for future expansion, but the most relevant additions to pursue next are:

- **Advanced range/result helpers** — ✅ completed (Nov 10, 2025). Both JVM and native now surface `MappedKeyValue`, `MappedRangeResult`, and helper collectors mirroring the Java API.
- **Directory-layer helpers and exceptions** — ✅ completed (Nov 15, 2025). Common helpers plus native exception parity ensure consistent behavior.
- **ByteArrayUtil helpers** — ✅ completed (Nov 18, 2025). Shared helper mirrors JVM utilities for concatenation and printable formats.
- **FastByteComparisons + IterableComparator** — ✅ completed (Nov 19, 2025). Shared comparison helpers unlock consistent tuple/byte ordering.
- **Tuple utility helpers** — ✅ completed (Nov 19, 2025). Float/double encoders and string utilities now available for both targets.
- **Options helpers** — ✅ completed (Nov 19, 2025). Shared DSL/sets mirror JVM `OptionsSet` flows for both database and transaction contexts.

If any of these become necessary, we can promote them from the “deferred” section into focused implementation tasks and extend the native API accordingly.

---

Keep this document up to date as native implementations land or new API areas are added. Once native parity is achieved, consider transforming this into a public roadmap section in the main README.
