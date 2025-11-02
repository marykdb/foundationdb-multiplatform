package maryk.foundationdb

public actual enum class ConflictRangeType {
    READ,
    WRITE
}

internal fun ConflictRangeType.toNativeCode(): Int = when (this) {
    ConflictRangeType.READ -> 0
    ConflictRangeType.WRITE -> 1
}
