// Compiled copy of the example in documentation/native-layout-upgrade.md.
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
