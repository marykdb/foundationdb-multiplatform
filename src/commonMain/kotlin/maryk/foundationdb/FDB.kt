package maryk.foundationdb

expect class FDB {
    fun options(): NetworkOptions
    fun open(): Database
    fun open(clusterFilePath: String): Database
    fun setUnclosedWarning(enabled: Boolean)
    fun shutdown()

    companion object {
        fun isAPIVersionSelected(): Boolean
        fun instance(): FDB
        fun selectAPIVersion(version: Int): FDB
    }
}
