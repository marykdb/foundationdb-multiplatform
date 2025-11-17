import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform") version "2.2.10"
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.0"
}

group = "io.maryk.foundationdb"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val coroutinesVersion = "1.9.0"
val foundationDbDir = rootProject.projectDir.resolve("foundationdb")
val foundationDbScriptsDir = foundationDbDir.resolve("scripts")
val foundationDbVersion = "7.3.71"
val foundationDbNativeDir = layout.buildDirectory.dir("foundationdb")
val foundationDbApiVersion = 730
val foundationDbReleaseBranch = "release-${foundationDbVersion.substringBeforeLast('.')}"

extensions.extraProperties["foundationDbVersion"] = foundationDbVersion
extensions.extraProperties["foundationDbNativeDir"] = foundationDbNativeDir
val foundationDbHeadersDir = foundationDbNativeDir.map { it.dir("headers") }
val foundationDbHeadersIncludeDir = foundationDbHeadersDir.map { it.dir("include") }
val downloadFoundationDbHeaders = tasks.register("downloadFoundationDbHeaders") {
    val headersUrl = "https://github.com/apple/foundationdb/releases/download/$foundationDbVersion/fdb-headers-$foundationDbVersion.tar.gz"
    val includeDirProvider = foundationDbHeadersIncludeDir
    inputs.property("version", foundationDbVersion)
    outputs.dir(includeDirProvider)
    doLast {
        val includeDir = includeDirProvider.get().asFile
        val marker = includeDir.parentFile.resolve(".version")
        val requiredHeaders = listOf("fdb_c.h", "fdb_c_options.g.h", "fdb_c_apiversion.g.h", "fdb.options", "fdb_c_types.h")
        val markerMatches = marker.isFile && marker.readText().trim() == foundationDbVersion && includeDir.isDirectory
        val headersPresent = requiredHeaders.all { includeDir.resolve(it).isFile }
        if (markerMatches && headersPresent) {
            return@doLast
        }
        includeDir.parentFile.deleteRecursively()
        includeDir.mkdirs()
        val archive = temporaryDir.resolve("fdb-headers.tar.gz")
        URI(headersUrl).toURL().openStream().use { input ->
            Files.copy(input, archive.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        val tree = project.tarTree(project.resources.gzip(archive))
        project.copy {
            from(tree)
            into(includeDir)
        }
        val extraHeaders = listOf("fdb_c_types.h")
        val downloadBase = "https://raw.githubusercontent.com/apple/foundationdb/$foundationDbReleaseBranch/bindings/c/foundationdb"
        extraHeaders.forEach { headerName ->
            val targetFile = includeDir.resolve(headerName)
            URI("$downloadBase/$headerName").toURL().openStream().use { input ->
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        marker.writeText(foundationDbVersion)
    }
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            val localLib = foundationDbDir.resolve("bin/lib").absolutePath
            val sysLib = "/usr/local/lib"
            val pathSeparator = File.pathSeparator
            jvmArgs(
                "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "-Djava.library.path=${localLib}${pathSeparator}${sysLib}"
            )
            environment(
                mapOf(
                    "DYLD_LIBRARY_PATH" to localLib,
                    "LD_LIBRARY_PATH" to localLib,
                    "FDB_CLUSTER_FILE" to foundationDbDir.resolve("fdb.cluster").absolutePath,
                )
            )
        }
    }
    jvmToolchain(21)
    val macosArm64Target = macosArm64()
    val macosX64Target = macosX64()
    val linuxX64Target = linuxX64()
    val linuxArm64Target = linuxArm64()

    val nativeTargets = listOf(macosArm64Target, macosX64Target, linuxX64Target, linuxArm64Target)
    val headersIncludeDirProvider = foundationDbHeadersIncludeDir.map { it.asFile }
    val hostLibDir = foundationDbDir.resolve("bin/lib").absolutePath

    fun KotlinNativeTarget.configureFoundationDbInterop() {
        val includeDir = headersIncludeDirProvider.get()
        val interop = compilations.getByName("main").cinterops.maybeCreate("foundationdb").apply {
            defFile(project.file("src/nativeInterop/cinterop/foundationdb.def"))
            includeDirs(project.files(includeDir))
            compilerOpts("-I${includeDir.absolutePath}", "-DFDB_API_VERSION=$foundationDbApiVersion")
        }
        project.tasks.named(interop.interopProcessingTaskName).configure {
            dependsOn(downloadFoundationDbHeaders)
        }
    }

    nativeTargets.forEach { target ->
        target.configureFoundationDbInterop()
        target.binaries.all {
            linkerOpts("-L$hostLibDir", "-lfdb_c", "-Wl,-rpath,$hostLibDir")
            linkTaskProvider.configure {
                dependsOn(installFoundationDB)
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation("org.foundationdb:fdb-java:7.3.71")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
    }
}

val os = org.gradle.internal.os.OperatingSystem.current()!!

val installFoundationDB by tasks.registering(Exec::class) {
    group = "foundationdb"
    description = "Install or link FoundationDB binaries into foundationdb/bin"
    environment("VERBOSE", "1")
    commandLine("bash", foundationDbScriptsDir.resolve("install-foundationdb.sh").absolutePath)
}

val startFoundationDBForTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Start a local fdbserver for tests"
    dependsOn(installFoundationDB)
    commandLine("bash", foundationDbScriptsDir.resolve("run-fdb-for-tests.sh").absolutePath)
}

val stopFoundationDBForTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Stop the local fdbserver started for tests"
    commandLine("bash", foundationDbScriptsDir.resolve("stop-fdb-for-tests.sh").absolutePath)
    isIgnoreExitValue = true
}

tasks.named("jvmTest").configure {
    dependsOn(startFoundationDBForTests)
    finalizedBy(stopFoundationDBForTests)
}

tasks.withType<KotlinNativeTest>().configureEach {
    dependsOn(startFoundationDBForTests)
    finalizedBy(stopFoundationDBForTests)
    args = args + "--ktest_logger=SIMPLE"
    environment("FDB_CLUSTER_FILE", foundationDbDir.resolve("fdb.cluster").absolutePath)
    environment("DYLD_LIBRARY_PATH", foundationDbDir.resolve("bin/lib").absolutePath)
    environment("LD_LIBRARY_PATH", foundationDbDir.resolve("bin/lib").absolutePath)
    environment("PATH", foundationDbDir.resolve("bin").absolutePath + ":" + System.getenv("PATH"))
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kover {
    reports {
        total {
            html {
                htmlDir.set(layout.projectDirectory.dir("htmlReport"))
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

mavenPublishing {
    coordinates(artifactId = "foundationdb-multiplatform")

    pom {
        name.set("foundationdb-multiplatform")
        description.set("Kotlin Multiplatform FoundationDB interface")
        inceptionYear.set("2025")
        url.set("https://github.com/marykdb/foundationdb-multiplatform")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("jurmous")
                name.set("Jurriaan Mous")
            }
        }

        scm {
            url.set("https://github.com/marykdb/foundationdb-multiplatform")
            connection.set("scm:git:git://github.com/marykdb/foundationdb-multiplatform.git")
            developerConnection.set("scm:git:ssh://git@github.com:marykdb/foundationdb-multiplatform.git")
        }
    }
}
