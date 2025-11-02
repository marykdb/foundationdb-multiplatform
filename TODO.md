# FoundationDB Multiplatform TODO

This repository now exposes the core FoundationDB client surface as Kotlin Multiplatform `expect` APIs with JVM `actual` implementations. The next big milestone is to light up the native targets and expand coverage beyond the initial essentials.

## Remaining Work

- **Implement native `actual` bindings**  
  - Decide on interop strategy (C-API via Kotlin/Native cinterop, shared library loading, error handling).  
  - Port the coroutine bridging layer (`FdbFuture`, `fdbFutureFromSuspend`) to native event loops.  
  - Provide native counterparts for every class listed in the table below.
- **Augment API coverage**  
  - Surface additional FoundationDB types as needed (e.g., `MutationType` variants, range split helpers, tenant configuration, option sets).  
  - Mirror utility helpers used by Maryk datastore code (e.g., watchers, versionstamp helpers) once required.
- **Refine coroutine ergonomics**  
  - Audit usages in `maryk/store/foundationdb` and migrate remaining `CompletableFuture` code to the new `FdbFuture` abstractions.  
  - Add structured-concurrency friendly cancellation semantics for long-running range scans.
- **Testing and validation**  
  - Introduce JVM integration tests hitting a real or embedded FoundationDB instance.  
  - Plan native CI builds and runtime verification once native bindings land.
- **Documentation & Samples**  
  - Update module README with usage examples.  
  - Provide guidance for configuring native toolchains (library search paths, environment variables).

## Implementation Matrix (per FDB Java API)

Legend: ✅ Implemented, ⬜ Pending, ➖ Not currently planned / defer

### Core API (`com.apple.foundationdb`)

| Class / Component         | JVM | Native | Tested | Notes |
| ------------------------- | --- | ------ | ------ | ----- |
| `FDB`                     | ✅  | ⬜ | ✅ | entry point `selectAPIVersion`, `open` |
| `Database`                | ✅  | ⬜ | ✅ | wrapped in `maryk.foundationdb.Database` |
| `Tenant`                  | ✅  | ⬜ | ✅ | tenant context support |
| `Transaction`             | ✅  | ⬜ | ✅ | includes mutation helpers |
| `ReadTransaction`         | ✅  | ⬜ | ✅ | snapshot + read operations |
| `TransactionContext`      | ✅  | ⬜ | ✅ | surfaced via shared interface |
| `ReadTransactionContext`  | ✅  | ⬜ | ✅ | shared interface available via TransactionContext |
| `DatabaseOptions`         | ✅  | ⬜ | ✅ | extended txn defaults (cache, watches, retry) |
| `NetworkOptions`          | ✅  | ⬜ | ✅ | broad trace/TLS/buggify knobs exposed |
| `TransactionOptions`      | ✅  | ⬜ | ✅ | exposes major per-transaction knobs (retry, timeouts, system access, tags) |
| `TenantManagement`        | ✅  | ⬜ | ✅ | create/delete/list tenants bridged |
| `Cluster` / `ClusterOptions` | ✅  | ⬜ | ✅ | wrapper available for connect-close flows |
| `EventKeeper` / `MapEventKeeper` | ✅ | ⬜ | ✅ | basic event instrumentation available |
| `LocalityUtil`            | ✅  | ⬜ | ✅ | boundary key + address helpers exposed |
| `Range`, `KeySelector`, `KeyValue` | ✅ | ⬜ | ✅ | basic key-space primitives |
| `MutationType`            | ✅  | ⬜ | ✅ | enum mapping for `mutate` |
| `StreamingMode`           | ✅  | ⬜ | ✅ | enum bridged for range read batching hints |
| `RangeQuery` / `RangeResult*` | ✅ | ⬜ | ✅ | use `ReadTransaction.collectRange` + summaries |
| `Future*` specializations (`FutureResult`, `FutureVoid`, etc.) | ✅ | ⬜ | ✅ | use existing `CompletableFuture.toFdbFuture` bridge |
| `NativeFuture` / `NativeObjectWrapper` | ➖ | ➖ | ➖ | internal JNI plumbing, usually hidden |
| `ApiVersion`, `ConflictRangeType` | ✅ | ⬜ | ✅ | constants exposed in Kotlin API |

### Async Utilities (`com.apple.foundationdb.async`)

| Class / Component    | JVM | Native | Tested | Notes |
| -------------------- | --- | ------ | ------ | ----- |
| `AsyncIterable`      | ✅  | ⬜ | ✅ | wrapped with mapper-based bridge |
| `AsyncIterator`      | ✅  | ⬜ | ✅ | coroutine-friendly `next()` |
| `AsyncUtil`          | ✅  | ⬜ | ✅ | helpers to collect / iterate async streams |
| `CloneableException` | ➖ | ➖ | ➖ | currently unused directly |
| `Cancellable` / `CloseableAsyncIterator` | ✅ | ⬜ | ✅ | mapped to FDB async interfaces |

### Directory Layer (`com.apple.foundationdb.directory`)

| Class / Component      | JVM | Native | Tested | Notes |
| ---------------------- | --- | ------ | ------ | ----- |
| `DirectoryLayer`       | ✅  | ⬜ | ✅ | create/open directory paths |
| `DirectorySubspace`    | ✅  | ⬜ | ✅ | pack/unpack directory prefixes |
| `DirectoryPartition`   | ✅  | ⬜ | ✅ | typealias for partition subspaces |
| `Directory` (interface) | ✅ | ⬜ | ✅ | wrapper for directory operations |
| Exceptions (`Directory*Exception`) | ➖ | ➖ | ➖ | surface via native exceptions as needed |
| Utility classes (`PathUtil`, `DirectoryUtil`) | ➖ | ➖ | ➖ | consider static helper exposure |

### Tuple & Subspace (`com.apple.foundationdb.tuple` / `subspace`)

| Class / Component | JVM | Native | Tested | Notes |
| ----------------- | --- | ------ | ------ | ----- |
| `Tuple`           | ✅  | ⬜ | ✅ | packing/unpacking only |
| `Versionstamp`    | ✅  | ⬜ | ✅ | supports packing/unpacking of versionstamp tuples |
| `ByteArrayUtil` / `TupleUtil` | ➖ | ➖ | ➖ | helper utilities, optional surface |
| `Subspace`        | ✅  | ⬜ | ✅ | prefix helper for tuple-derived key ranges |

### Testing & Misc (`com.apple.foundationdb.testing`, etc.)

| Class / Component | JVM | Native | Tested | Notes |
| ----------------- | --- | ------ | ------ | ----- |
| Workload/test harness classes | ➖ | ➖ | ➖ | likely out of scope for production API |

### Project-specific Glue

| Component                        | JVM | Native | Tested | Notes |
| -------------------------------- | --- | ------ | ------ | ----- |
| `FdbFuture` abstraction          | ✅  | ⬜ | ✅ | unify async semantics |
| Coroutine helpers (`runSuspend`) | ✅  | ⬜ | ✅ | leverage native event loops |

✅ — Implemented and wired for JVM  
⬜ — Not yet implemented  
➖ — Currently deferred / optional

---

Keep this document up to date as native implementations land or new API areas are added. Once native parity is achieved, consider transforming this into a public roadmap section in the main README.
