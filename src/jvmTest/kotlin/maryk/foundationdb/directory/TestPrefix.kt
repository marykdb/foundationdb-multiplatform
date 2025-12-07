package maryk.foundationdb.directory

internal actual fun DirectorySubspace.testRawPrefix(): ByteArray {
    val delegateField = this::class.java.getDeclaredField("delegate")
    delegateField.isAccessible = true
    val delegate = delegateField.get(this)
    val keyMethod = delegate.javaClass.methods.firstOrNull { it.name == "key" && it.parameterCount == 0 }
    if (keyMethod != null) {
        return keyMethod.invoke(delegate) as ByteArray
    }
    val packMethod = delegate.javaClass.methods.firstOrNull { it.name == "pack" && it.parameterCount == 0 }
    if (packMethod != null) {
        return packMethod.invoke(delegate) as ByteArray
    }
    throw IllegalStateException("Unknown delegate type ${delegate.javaClass}")
}
