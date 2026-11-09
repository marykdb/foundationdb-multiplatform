# Upgrading old Native directory partitions

Normal applications should use the canonical Native directory API. Accessing a
partition with recognizable old Native metadata throws
`DirectoryMigrationRequiredException`, a `DirectoryVersionException` subtype.
Its `path` and `detectedLayout` identify the partition and legacy metadata layout.
Opening, creating through, checking existence of, listing inside, moving or removing
the affected partition is blocked instead of silently creating a second tree.
Listing its parent only lists names; it does not scan every descendant for legacy data.

This check uses known legacy nested metadata and inline child-pointer locations.
It is a Native safeguard. There is no database-wide scan, JVM detection or automatic
migration on application startup. Empty partitions without identifying metadata and
arbitrary raw subspace keys cannot reliably be classified from their bytes. Ordinary
Maryk directories do not need this conversion; a custom legacy partition ancestor can.

## Run an explicit offline migration

Stop every writer to source and destination and retain a backup. Configure disjoint
metadata/content namespaces, inspect the complete source inventory and review it.
Only the migration job selects the old source layout. The destination is canonical.
For example, a custom Native maintenance job can use:

```kotlin
import maryk.foundationdb.Database
import maryk.foundationdb.directory.DirectoryLayout
import maryk.foundationdb.directory.DirectoryTreeMigration
import maryk.foundationdb.readSuspend
import maryk.foundationdb.subspace.Subspace

suspend fun migrateOffline(
    database: Database,
    sourceNodes: Subspace,
    sourceContent: Subspace,
    destinationNodes: Subspace,
    destinationContent: Subspace,
    detectedLayout: DirectoryLayout,
) {
    val migration = DirectoryTreeMigration(
        DirectoryTreeMigration.Configuration(sourceNodes, sourceContent, detectedLayout),
        DirectoryTreeMigration.Configuration(destinationNodes, destinationContent),
    )
    val inventory = mutableListOf<DirectoryTreeMigration.Entry>()
    val pending = ArrayDeque<List<String>>()
    pending.addAll(database.readSuspend { migration.inspectRoots(it) })
    while (pending.isNotEmpty()) {
        val path = pending.removeFirst()
        val entry = database.readSuspend { migration.inspect(it, path) }
        inventory += entry
        pending.addAll(entry.children)
    }
    // Review the inventory before executing this step in your maintenance job.
    migration.copyAndVerify(database, inventory, keyLimit = 100)
}
```

The runner prepares the destination, copies parent directories before children in
separately committed key/byte batches, then verifies all entries and root names.
It returns only after verification succeeds. Switch application configuration to
the destination layer afterward; source data is retained for rollback.

After interruption, rebuild the inventory with the same configuration and rerun.
Committed identical batches are safe to replay; this simple runner starts each entry
from the beginning. For large jobs that require saved cursors, use `copyBatch` and
`verifyBatch` directly and persist each cursor only after commit.

Mixed layouts, destination collisions and records stored directly at partition roots
stop migration. Reconcile them explicitly; do not catch the error and continue startup
with an empty store. Raw subspaces need an explicit old-prefix definition and the
separate prefix migration workflow. See [migration details](migrations.md).
