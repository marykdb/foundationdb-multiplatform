[![Download](https://img.shields.io/maven-central/v/io.maryk.foundationdb/foundationdb-multiplatform)](https://central.sonatype.com/artifact/io.maryk.foundationdb/foundationdb-multiplatform)

# FoundationDB Multiplatform

Kotlin Multiplatform bindings for [FoundationDB](https://apple.github.io/foundationdb/). The project wraps the official
Apple-provided Java and C client libraries so the same typed API surface (transactions, futures, options, tuple utilities,
etc.) can be used from JVM code as well as native Apple/Linux binaries.

The repository currently tracks FoundationDB **7.3** and is meant for application developers that want:

- A single, coroutine-friendly API surface shared between JVM and Kotlin/Native targets.
- Typed transaction, option, range, tuple and directory APIs on JVM and Native.

## Supported targets

| Platform | Targets |
| -------- | ------- |
| JVM      | linux, macOS (Intel + Apple Silicon) |
| Native   | `macosArm64`, `macosX64`, `linuxX64`, `linuxArm64` |

> **Windows**: upstream FoundationDB no longer provides Windows client binaries, so the mingw target and the related
> installers were intentionally not implemented. If Apple or another source resumes publishing Windows builds we can revisit this.

## Getting started

The artifacts are published to Maven Central under `io.maryk.foundationdb:foundationdb-multiplatform`. Add the dependency to
any source set that should talk to FoundationDB:

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.maryk.foundationdb:foundationdb-multiplatform:7.3.81")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
    }
}
```

On the JVM the wrapper delegates straight to the official Java bindings. On native targets the project links against the
C client (`libfdb_c`) that ships with FoundationDB. You must install the FoundationDB client libraries for the host OS
before running your binaries; the provided scripts under `foundationdb/scripts` install/copy these binaries into
`foundationdb/bin` (project-local, preferred over system paths).

### Minimal example

```kotlin
import kotlinx.coroutines.runBlocking
import maryk.foundationdb.ApiVersion
import maryk.foundationdb.FDB
import maryk.foundationdb.readSuspend
import maryk.foundationdb.tuple.Tuple

fun main() = runBlocking {
    FDB.selectAPIVersion(ApiVersion.LATEST)
    val database = FDB.instance().open()
    try {
        val key = Tuple.from("hello").pack()
        database.run { it.set(key, "world".encodeToByteArray()) }
        val value = database.readSuspend { it.get(key).await() }
        println(value?.decodeToString())
    } finally {
        database.close()
    }
}
```

See the [portable API guide](documentation/api-design.md) for coroutine adapters, streaming,
JVM integration and compatibility.

Additional examples live in `src/commonTest/kotlin/maryk/foundationdb` and mirror all major FoundationDB features
(range iterators, tuple decoding helpers, management API, etc.).

## Development workflow

- `./gradlew jvmTest` – installs a local FoundationDB server (macOS/Linux), starts it, and runs the JVM tests.
- `./gradlew macosArm64Test`, `macosX64Test`, `linuxX64Test`, `linuxArm64Test` – build/link native binaries and run the
  platform specific tests. These tasks also install FoundationDB locally if needed.
- `foundationdb/scripts/install-foundationdb.sh` – helper that downloads the official Apple release for the current host
  and copies `fdbserver`, `fdbcli`, and `libfdb_c` into `foundationdb/bin` for local development.

> Native builds require the FoundationDB client headers. Gradle downloads them automatically (`fdb-headers-${version}.tar.gz`)
> when building Kotlin/Native targets; the generated headers live under `build/foundationdb/headers`.

## Contributing

Bug reports, feature requests, and pull requests are very welcome. Please open an issue describing the desired change.

## License

Apache License 2.0 – see [LICENSE](LICENSE) for the full text.
