package maryk.foundationdb.directory

object PathUtil {
    fun join(path1: List<String>, path2: List<String>): List<String> =
        buildList(path1.size + path2.size) {
            addAll(path1)
            addAll(path2)
        }

    fun extend(path: List<String>, vararg subPaths: String): List<String> =
        join(path, subPaths.toList())

    fun from(vararg subPaths: String): List<String> =
        subPaths.toList()

    fun popFront(path: List<String>): List<String> {
        require(path.isNotEmpty()) { "Path contains no elements." }
        return path.drop(1)
    }

    fun popBack(path: List<String>): List<String> {
        require(path.isNotEmpty()) { "Path contains no elements." }
        return path.dropLast(1)
    }
}
