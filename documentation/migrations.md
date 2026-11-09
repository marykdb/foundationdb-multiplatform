# Native layout compatibility and migration

For the canonical Native error-and-runner workflow, see the [short upgrade guide](native-layout-upgrade.md).

This unreleased API update changes two Native defaults to match the official Java client:
`Subspace(tuple, rawPrefix)` uses `rawPrefix + tuple.pack()`, and directory partitions
place nested metadata under `partitionPrefix + 0xFE`. Ordinary directory metadata,
Long tuple encodings, and raw-prefix-only Subspace constructors are unchanged.
These are breaking changes and must be announced in the release notes.

## Open existing data explicitly

Use `subspace(tuple, rawPrefix, SubspaceLayout.LEGACY_NATIVE)` for namespaces written
with the previous Native mixed-prefix constructor. The legacy factory works on JVM too.
For old Native partitions use `DirectoryLayer.forLayout(DirectoryLayout.LEGACY_NATIVE,
nodeSubspace, contentSubspace)`. The Native backend implements both layouts. The JVM
backend uses the official Java layer and rejects legacy partition mode: migrate on Native
before switching a legacy partition database to JVM. No data is automatically moved.
Opening a partition with initialized metadata for the other mode fails instead of silently
creating a parallel tree. This guard does not infer the layout of arbitrary raw user keys.

Some older full-path Native calls stored partition children inline in the parent
metadata tree. Open these through the Native-only read-only
`LEGACY_NATIVE_FLAT_PARTITIONS` view and pass that layout in the migration source
Configuration. Canonical/nested modes reject inline children before creating a second
tree. Mixed inline and nested trees are ambiguous and require explicit reconciliation;
the library refuses to choose which copy is authoritative.

## Offline prefix migration

Stop all writers to source and destination namespaces and retain a backup. Construct a
`PrefixMigration(legacySubspace, canonicalSubspace)` with disjoint nonempty prefixes.
Call `inspect` in a read transaction, review the returned batch, then `apply` in a write
transaction and commit. Repeat inspection from the beginning until no keys remain.
The tool checks source values and destination collisions before staging mutations; each
batch atomically writes destination keys and clears source keys. Replaying an applied
batch is safe. Use `Range.startsWith` for final source/destination verification.
The tool does not rewrite values containing application-specific key references.

## Offline directory-tree migration

A directory tree needs metadata-aware migration; do not move directory metadata with
`PrefixMigration`. Configure separate source and destination directory layers, each with
explicit content namespaces and disjoint nonempty metadata namespaces. The source
content namespace may be empty for an existing default/global directory layer; the
destination content namespace must be nonempty. Keep
both metadata namespaces outside both content namespaces. Select LEGACY_NATIVE for
the source and JAVA_CANONICAL for the destination. Construct `DirectoryTreeMigration` with source and destination `Configuration` values
containing each metadata subspace, content subspace and layout. The helper validates
that the nonempty configured namespaces are disjoint and opens the matching directory
layers itself. Inspection additionally checks every allocated source directory/partition
prefix against both destination namespaces, including a global source configuration.

Call `inspectRoots` and recursively `inspect` every directory/partition child to build a
complete review inventory. Call `prepare(transaction, inventory)` and commit before
copying. This validates inventory completeness and initially empty destination namespaces,
and writes a persistent migration owner marker for safe restart. Repeating prepare with
the same configuration is supported after partial copying. For each inventoried entry:

1. Call `inspect(transaction, path)` and review its layer, children, and partition status.
2. Call `copyBatch` in write transactions until `Progress.complete`. Persist `nextSuffix`
   only after the corresponding transaction commits; replay after an uncertain commit
   is safe. The caller controls transaction retries, checkpoints and the offline window.
3. Recursively process every path in `Entry.children`, including partition children.
4. Call `verifyBatch` over every completed directory and partition, advancing its cursor
   until complete. Call `verifyRoots` to compare root child lists as well.
5. Switch the application's configured directory layer only after all verification passes.

The destination recreates metadata and allocates new data prefixes through its directory
API. Copying uses bounded key/byte batches and rejects conflicting destination values.
Verification compares children, layers, keys and values. The source remains intact for
rollback; source deletion is deliberately separate. Verify that application values do not
embed old physical keys, or migrate those application-specific references separately.

Released Native APIs also allowed packing user keys directly at a partition root.
Those values have no ordinary-directory destination in the canonical layout. Preparation,
copying and verification reject such unaccounted data with the partition path rather than
reporting a successful incomplete migration. Keep a backup and explicitly move that data
into an ordinary directory before preparing the migration again. Data owned by known
child directories is allowed, including children with manually allocated prefixes.

The migration helpers are offline APIs, not an automatic online migration service. All
writers and unrelated destination data must remain excluded throughout copying and
verification. An Entry is an in-memory review object; to resume a process, inspect its path
again and restore the last committed suffix checkpoint.

### Restart and fault regression coverage

`MigrationRestartTest` stages and abandons an uncommitted batch, forces an actual FDB
read conflict at commit, then resumes with a fresh database connection and migration
object. It re-inspects the inventory, replays a committed batch whose cursor was lost,
continues from the saved cursor, and compares every source/destination byte. These are
transaction-boundary and connection-restart tests, not an operating-system crash or
power-loss simulation. Persist cursors only after commit; offline writer exclusion is
still required throughout copying and verification.
