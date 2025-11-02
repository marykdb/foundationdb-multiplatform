package maryk.foundationdb

expect class FDB {
    fun options(): NetworkOptions
    fun open(): Database
    fun open(clusterFilePath: String): Database
    fun createCluster(clusterFilePath: String? = null): Cluster
    fun setUnclosedWarning(enabled: Boolean)

    companion object {
        fun isAPIVersionSelected(): Boolean
        fun instance(): FDB
        fun selectAPIVersion(version: Int): FDB
    }
}
