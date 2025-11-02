package maryk.foundationdb

expect class Cluster {
    fun options(): ClusterOptions
    fun openDatabase(): Database
    fun close()
}

expect class ClusterOptions
