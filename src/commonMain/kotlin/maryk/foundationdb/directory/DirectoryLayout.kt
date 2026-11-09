package maryk.foundationdb.directory

/** Native partition layout; canonical partitions interoperate with the official Java directory layer. */
enum class DirectoryLayout {
    JAVA_CANONICAL,
    LEGACY_NATIVE,
    /** Read-only migration view for old full-path APIs that stored partition children inline. */
    LEGACY_NATIVE_FLAT_PARTITIONS
}
