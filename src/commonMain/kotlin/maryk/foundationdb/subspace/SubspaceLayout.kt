package maryk.foundationdb.subspace

import maryk.foundationdb.tuple.Tuple

/** Layout selection is explicit and never inferred by reading existing keys. */
enum class SubspaceLayout { JAVA_CANONICAL, LEGACY_NATIVE }

/** New namespaces use the canonical layout. Use LEGACY_NATIVE to open old mixed-prefix namespaces. */
fun subspace(prefix: Tuple, rawPrefix: ByteArray, layout: SubspaceLayout = SubspaceLayout.JAVA_CANONICAL): Subspace =
    Subspace(if (layout == SubspaceLayout.JAVA_CANONICAL) rawPrefix + prefix.pack() else prefix.pack() + rawPrefix)
