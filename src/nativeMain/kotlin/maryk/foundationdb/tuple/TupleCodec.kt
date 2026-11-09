package maryk.foundationdb.tuple

import maryk.foundationdb.NativeEnvironment
import maryk.foundationdb.ByteArrayUtil
import maryk.foundationdb.StringUtil
import maryk.foundationdb.TupleUtil
import kotlin.text.CharacterCodingException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal fun versionstampOffsetSize(): Int = if (NativeEnvironment.selectedApiVersion() < 520) 2 else 4

internal object TupleCodec {
    private const val NULL_CODE: Byte = 0x00
    private const val BYTES_CODE: Byte = 0x01
    private const val STRING_CODE: Byte = 0x02
    private const val NESTED_CODE: Byte = 0x05
    private const val INT_ZERO_CODE: Byte = 0x14
    private const val NEG_INT_START: Byte = 0x0b
    private const val POS_INT_END: Byte = 0x1d
    private const val FLOAT_CODE: Byte = 0x20
    private const val DOUBLE_CODE: Byte = 0x21
    private const val FALSE_CODE: Byte = 0x26
    private const val TRUE_CODE: Byte = 0x27
    private const val VERSIONSTAMP_CODE: Byte = 0x33
    private const val UUID_CODE: Byte = 0x30
    private val NULL_ESCAPED = byteArrayOf(0x00, 0xFF.toByte())

    fun pack(items: List<Any?>, allowIncompleteVersionstamp: Boolean): ByteArray {
        val state = EncodeState(getPackedSize(items, nested = false))
        encodeAll(state, items, nested = false)
        require(allowIncompleteVersionstamp || state.versionPos < 0) {
            "Incomplete Versionstamp included in vanilla tuple pack"
        }
        return state.toByteArray()
    }

    fun packWithVersionstamp(items: List<Any?>): ByteArray {
        val payloadSize = getPackedSize(items, nested = false)
        val state = EncodeState(payloadSize)
        encodeAll(state, items, nested = false)
        require(state.versionCount == 1) { "Exactly one incomplete Versionstamp is required" }
        require(state.versionPos >= 0) {
            "No incomplete Versionstamp included in tuple pack with versionstamp"
        }
        val suffix = ByteArray(versionstampOffsetSize())
        val offset = state.versionPos
        require(suffix.size != 2 || offset <= 0xffff) { "Versionstamp offset exceeds legacy API limit" }
        for (index in suffix.indices) suffix[index] = (offset ushr (index * 8)).toByte()
        val packed = state.toByteArray()
        val result = ByteArray(packed.size + suffix.size)
        packed.copyInto(result, 0)
        suffix.copyInto(result, packed.size)
        return result
    }

    fun unpack(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): List<Any?> {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        val state = DecodeState()
        val limit = offset + length
        var pos = offset
        while (pos < limit) {
            decode(state, bytes, pos, limit)
            pos = state.end
        }
        return state.values
    }

    private fun encodeAll(state: EncodeState, items: List<Any?>, nested: Boolean) {
        for (item in items) {
            encode(state, item, nested)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun encode(state: EncodeState, value: Any?, nested: Boolean) {
        when (value) {
            null -> if (nested) state.addBytes(NULL_ESCAPED) else state.addByte(NULL_CODE)
            is ByteArray -> encodeBytes(state, value)
            is String -> encodeString(state, value)
            is Float -> {
                state.addByte(FLOAT_CODE)
                state.addInt(TupleUtil.encodeFloatBits(value))
            }
            is Double -> {
                state.addByte(DOUBLE_CODE)
                state.addLong(TupleUtil.encodeDoubleBits(value))
            }
            is Boolean -> state.addByte(if (value) TRUE_CODE else FALSE_CODE)
            is TupleInteger -> encodeInteger(state, value)
            is Number -> encodeLong(state, value.toLong())
            is Versionstamp -> encodeVersionstamp(state, value)
            is Uuid -> encodeUuid(state, value)
            is Tuple -> encodeTuple(state, value.elements)
            is List<*> -> encodeList(state, value)
            else -> throw IllegalArgumentException("Unsupported tuple element type: ${value::class}")
        }
    }

    private fun encodeBytes(state: EncodeState, bytes: ByteArray) {
        state.addByte(BYTES_CODE)
        appendEscaped(state, bytes)
        state.addByte(NULL_CODE)
    }

    private fun encodeString(state: EncodeState, value: String) {
        StringUtil.validate(value)
        state.addByte(STRING_CODE)
        appendEscaped(state, value.encodeToByteArray())
        state.addByte(NULL_CODE)
    }

    private fun encodeInteger(state: EncodeState, number: TupleInteger) {
        val bytes = number.magnitudeBytes()
        require(bytes.size <= 255) { "Tuple integer magnitude exceeds 255 bytes" }
        if (bytes.isEmpty()) { state.addByte(INT_ZERO_CODE); return }
        val positive = number.sign > 0
        if (bytes.size <= 8) state.addByte((INT_ZERO_CODE + if (positive) bytes.size else -bytes.size).toByte())
        else {
            state.addByte(if (positive) POS_INT_END else NEG_INT_START)
            state.addByte((if (positive) bytes.size else bytes.size xor 255).toByte())
        }
        for (byte in bytes) state.addByte(if (positive) byte else (byte.toInt() xor 255).toByte())
    }

    private fun encodeLong(state: EncodeState, number: Long) {
        if (number == 0L) {
            state.addByte(INT_ZERO_CODE)
            return
        }
        val positive = number > 0
        val n = TupleUtil.minimalByteCount(if (positive) number else -number)
        val code = (INT_ZERO_CODE + if (positive) n else -n).toByte()
        state.addByte(code)
        val value = if (positive) number else number - 1
        for (shift in (n - 1) downTo 0) {
            val byte = ((value ushr (shift * 8)) and 0xFF).toByte()
            state.addByte(byte)
        }
    }

    private fun encodeVersionstamp(state: EncodeState, versionstamp: Versionstamp) {
        state.addByte(VERSIONSTAMP_CODE)
        if (versionstamp.isComplete) {
            state.addBytes(versionstamp.bytes)
        } else {
            state.addBytes(versionstamp.bytes, markVersion = true)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun encodeUuid(state: EncodeState, uuid: Uuid) {
        state.addByte(UUID_CODE)
        uuid.toLongs { msb, lsb -> state.addLong(msb); state.addLong(lsb) }
    }

    private fun encodeList(state: EncodeState, list: List<*>) {
        state.addByte(NESTED_CODE)
        list.forEach { encode(state, it, nested = true) }
        state.addByte(NULL_CODE)
    }

    private fun encodeTuple(state: EncodeState, items: List<Any?>) {
        encodeList(state, items)
    }

    private fun appendEscaped(state: EncodeState, data: ByteArray) {
        data.forEach { byte ->
            if (byte == NULL_CODE) {
                state.addByte(NULL_CODE)
                state.addByte(0xFF.toByte())
            } else {
                state.addByte(byte)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun decode(state: DecodeState, bytes: ByteArray, pos: Int, limit: Int) {
        val code = bytes[pos]
        val start = pos + 1
        when (code) {
            NULL_CODE -> state.add(null, start)
            BYTES_CODE -> {
                val end = findTerminator(bytes, start, limit)
                val decoded = unescape(bytes, start, end)
                state.add(decoded, end + 1)
            }
            STRING_CODE -> {
                val end = findTerminator(bytes, start, limit)
                val decoded = unescape(bytes, start, end)
                val value = try { decoded.decodeToString(throwOnInvalidSequence = true) }
                    catch (error: CharacterCodingException) { throw IllegalArgumentException("Malformed UTF-8 string", error) }
                state.add(value, end + 1)
            }
            FLOAT_CODE -> {
                require(Float.SIZE_BYTES <= limit - start) { "Truncated tuple float" }
                val raw = bytes.getInt(start)
                state.add(TupleUtil.decodeFloatBits(raw), start + Int.SIZE_BYTES)
            }
            DOUBLE_CODE -> {
                require(Double.SIZE_BYTES <= limit - start) { "Truncated tuple double" }
                val raw = bytes.getLong(start)
                state.add(TupleUtil.decodeDoubleBits(raw), start + Long.SIZE_BYTES)
            }
            FALSE_CODE -> state.add(false, start)
            TRUE_CODE -> state.add(true, start)
            VERSIONSTAMP_CODE -> {
                val end = start + Versionstamp.LENGTH
                require(end <= limit) { "Invalid tuple (possible truncation)" }
                val value = Versionstamp.fromBytes(bytes.copyOfRange(start, end))
                state.add(value, end)
            }
            UUID_CODE -> {
                require(2 * Long.SIZE_BYTES <= limit - start) { "Truncated tuple UUID" }
                val msb = bytes.getLong(start)
                val lsb = bytes.getLong(start + Long.SIZE_BYTES)
                state.add(Uuid.fromLongs(msb, lsb), start + 2 * Long.SIZE_BYTES)
            }
            NESTED_CODE -> {
                val nestedItems = mutableListOf<Any?>()
                var cursor = start
                var foundTerminator = false
                while (cursor < limit) {
                    if (bytes[cursor] == NULL_CODE && (cursor + 1 >= limit || bytes[cursor + 1] != 0xFF.toByte())) {
                        cursor += 1
                        foundTerminator = true
                        break
                    } else if (bytes[cursor] == NULL_CODE && cursor + 1 < limit && bytes[cursor + 1] == 0xFF.toByte()) {
                        nestedItems += null
                        cursor += 2
                    } else {
                        val nestedState = DecodeState()
                        decode(nestedState, bytes, cursor, limit)
                        nestedItems += nestedState.values.first()
                        cursor = nestedState.end
                    }
                }
                require(foundTerminator) { "No terminator found for nested tuple" }
                state.add(nestedItems, cursor)
            }
            else -> decodeNumericOrExtended(state, bytes, code, start, limit)
        }
    }

    private fun decodeNumericOrExtended(
        state: DecodeState,
        bytes: ByteArray,
        code: Byte,
        start: Int,
        limit: Int
    ) {
        when {
            code >= NEG_INT_START && code <= POS_INT_END -> {
                val positive = code >= INT_ZERO_CODE
                val extended = code == POS_INT_END || code == NEG_INT_START
                var cursor = start
                val length = if (extended) {
                    require(cursor < limit) { "Truncated integer length" }
                    val size = bytes[cursor++].toInt() and 255
                    if (positive) size else size xor 255
                } else if (positive) code - INT_ZERO_CODE else INT_ZERO_CODE - code
                require(length <= limit - cursor) { "Truncated tuple integer" }
                if (!extended) {
                    var magnitude = 0uL
                    for (index in 0 until length) {
                        val byte = bytes[cursor + index].toInt() and 255
                        magnitude = (magnitude shl 8) or (if (positive) byte else byte xor 255).toULong()
                    }
                    val bound = if (positive) Long.MAX_VALUE.toULong() else 1uL shl 63
                    if (magnitude <= bound) {
                        state.add(if (positive) magnitude.toLong() else -magnitude.toLong(), cursor + length)
                        return
                    }
                }
                val magnitude = ByteArray(length) { index ->
                    val byte = bytes[cursor + index]
                    if (positive) byte else (byte.toInt() xor 255).toByte()
                }
                val integer = TupleInteger.fromMagnitude(if (positive) 1 else -1, magnitude)
                val value: Any = if (extended) integer else try { integer.toLongExact() } catch (_: ArithmeticException) { integer }
                state.add(value, cursor + length)
            }
            else -> throw IllegalArgumentException("Unsupported tuple type code: $code")
        }
    }

    private fun findTerminator(bytes: ByteArray, start: Int, limit: Int): Int {
        var index = start
        while (index < limit) {
            if (bytes[index] == NULL_CODE) {
                if (index + 1 < limit && bytes[index + 1] == 0xFF.toByte()) {
                    index += 2
                } else {
                    return index
                }
            } else {
                index++
            }
        }
        throw IllegalArgumentException("No terminator found for byte/string element")
    }

    private fun unescape(bytes: ByteArray, start: Int, end: Int): ByteArray {
        var escapes = 0
        var index = start
        while (index < end) {
            if (bytes[index++] == NULL_CODE) { escapes++; index++ }
        }
        if (escapes == 0) return bytes.copyOfRange(start, end)
        val output = ByteArray(end - start - escapes)
        index = start
        var target = 0
        while (index < end) {
            val value = bytes[index++]
            output[target++] = value
            if (value == NULL_CODE) index++
        }
        return output
    }

    @OptIn(ExperimentalUuidApi::class)
    fun getPackedSize(items: List<Any?>, nested: Boolean): Int {
        var size = 0
        for (item in items) {
            size += when (item) {
                null -> if (nested) 2 else 1
                is ByteArray -> 2 + item.size + ByteArrayUtil.nullCount(item)
                is String -> 2 + StringUtil.packedSize(item)
                is Float -> 1 + Float.SIZE_BYTES
                is Double -> 1 + Double.SIZE_BYTES
                is Boolean -> 1
                is TupleInteger -> item.magnitudeSize.let { require(it <= 255); 1 + it + if (it > 8) 1 else 0 }
                is Number -> 1 + TupleUtil.minimalByteCount(item.toLong())
                is Versionstamp -> 1 + Versionstamp.LENGTH
                is Uuid -> 1 + 2 * Long.SIZE_BYTES
                is List<*> -> 2 + getPackedSize(item, true)
                is Tuple -> 2 + getPackedSize(item.elements, true)
                else -> throw IllegalArgumentException("Unsupported tuple element type: ${item::class}")
            }
        }
        return size
    }

    private class EncodeState(expectedSize: Int) {
        private val buffer = ByteArray(expectedSize)
        private var position = 0
        var versionCount: Int = 0
            private set
        var versionPos: Int = -1
            private set

        fun addByte(byte: Byte) {
            buffer[position++] = byte
        }

        fun addBytes(bytes: ByteArray, markVersion: Boolean = false) {
            bytes.copyInto(buffer, position)
            if (markVersion) {
                versionCount++
                if (versionPos < 0) {
                    versionPos = position
                }
            }
            position += bytes.size
        }

        fun addInt(value: Int) {
            addByte(((value shr 24) and 0xFF).toByte())
            addByte(((value shr 16) and 0xFF).toByte())
            addByte(((value shr 8) and 0xFF).toByte())
            addByte((value and 0xFF).toByte())
        }

        fun addLong(value: Long) {
            addInt((value shr 32).toInt())
            addInt(value.toInt())
        }

        fun toByteArray(): ByteArray = if (position == buffer.size) buffer else buffer.copyOf(position)
    }

    private class DecodeState {
        val values = mutableListOf<Any?>()
        var end: Int = 0
            private set

        fun add(value: Any?, nextPos: Int) {
            values += value
            end = nextPos
        }
    }

    private fun ByteArray.getInt(start: Int): Int =
        ((this[start].toInt() and 0xFF) shl 24) or
            ((this[start + 1].toInt() and 0xFF) shl 16) or
            ((this[start + 2].toInt() and 0xFF) shl 8) or
            (this[start + 3].toInt() and 0xFF)

    private fun ByteArray.getLong(start: Int): Long =
        ((getInt(start).toLong() shl 32) or (getInt(start + 4).toLong() and 0xffffffffL))

}
