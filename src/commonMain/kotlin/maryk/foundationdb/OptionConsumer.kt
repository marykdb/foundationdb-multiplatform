package maryk.foundationdb

/** Raw upstream option code and encoded bytes; null means no parameter. Prefer typed setters. */
fun interface OptionConsumer {
    fun setOption(code: Int, value: ByteArray?)
}
