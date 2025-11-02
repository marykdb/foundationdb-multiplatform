import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("multiplatform") version "2.2.10"
}

group = "io.maryk.foundationdb"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val coroutinesVersion = "1.9.0"
val foundationDbDir = rootProject.projectDir.resolve("foundationdb")
val foundationDbScriptsDir = foundationDbDir.resolve("scripts")

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            val localLib = foundationDbDir.resolve("bin/lib").absolutePath
            val sysLib = "/usr/local/lib"
            val pathSeparator = System.getProperty("path.separator")
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

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.foundationdb:fdb-java:7.3.71")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val jvmTest by getting
    }

}

val os = org.gradle.internal.os.OperatingSystem.current()

val installFoundationDB by tasks.registering(Exec::class) {
    group = "foundationdb"
    description = "Install or link FoundationDB binaries into foundationdb/bin"
    if (os.isWindows) {
        commandLine(
            "powershell",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            foundationDbScriptsDir.resolve("install-foundationdb.ps1").absolutePath
        )
    } else {
        environment("VERBOSE", "1")
        commandLine("bash", foundationDbScriptsDir.resolve("install-foundationdb.sh").absolutePath)
    }
}

val startFoundationDBForTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Start a local fdbserver for tests"
    dependsOn(installFoundationDB)
    if (!os.isWindows) {
        commandLine("bash", foundationDbScriptsDir.resolve("run-fdb-for-tests.sh").absolutePath)
    } else {
        commandLine("bash", "-lc", "echo 'Please start FoundationDB on Windows before running tests' && exit 0")
    }
}

val stopFoundationDBForTests by tasks.registering(Exec::class) {
    group = "verification"
    description = "Stop the local fdbserver started for tests"
    if (!os.isWindows) {
        commandLine("bash", foundationDbScriptsDir.resolve("stop-fdb-for-tests.sh").absolutePath)
        isIgnoreExitValue = true
    } else {
        commandLine("bash", "-lc", "true")
    }
}

tasks.named("jvmTest").configure {
    dependsOn(startFoundationDBForTests)
    finalizedBy(stopFoundationDBForTests)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
