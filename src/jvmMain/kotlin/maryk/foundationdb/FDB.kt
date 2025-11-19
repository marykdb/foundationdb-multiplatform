package maryk.foundationdb

actual class FDB internal constructor(
    internal val delegate: com.apple.foundationdb.FDB
) {
    actual fun options(): NetworkOptions = NetworkOptions(delegate.options())

    actual fun open(): Database = Database(delegate.open())

    actual fun open(clusterFilePath: String): Database = Database(delegate.open(clusterFilePath))

    actual fun setUnclosedWarning(enabled: Boolean) {
        delegate.setUnclosedWarning(enabled)
    }

    actual fun shutdown() {
        delegate.stopNetwork()
    }

    actual companion object {
        actual fun isAPIVersionSelected(): Boolean = com.apple.foundationdb.FDB.isAPIVersionSelected()

        actual fun instance(): FDB = FDB(com.apple.foundationdb.FDB.instance())

        actual fun selectAPIVersion(version: Int): FDB =
            FDB(com.apple.foundationdb.FDB.selectAPIVersion(version))
    }
}
