package maryk.foundationdb

actual class Cluster internal constructor(
    internal val delegate: com.apple.foundationdb.Cluster
) {
    actual fun options(): ClusterOptions = ClusterOptions(delegate.options())

    actual fun openDatabase(): Database = Database(delegate.openDatabase())

    actual fun close() {
        delegate.close()
    }
}

actual class ClusterOptions internal constructor(
    internal val delegate: com.apple.foundationdb.ClusterOptions
)
