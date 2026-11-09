import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins { kotlin("multiplatform") version "2.4.21" }
repositories {
    maven {
        url = uri(providers.gradleProperty("bindingRepository").get())
        content { includeGroup("io.maryk.foundationdb") }
        if (providers.gradleProperty("pomOnly").orNull == "true") {
            metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() }
        }
    }
    mavenCentral { content { excludeGroup("io.maryk.foundationdb") } }
}
kotlin {
    jvm()
    jvmToolchain(17)
    sourceSets.commonMain.dependencies {
        implementation("io.maryk.foundationdb:${providers.gradleProperty("bindingCoordinate").get()}:${providers.gradleProperty("bindingVersion").get()}")
    }
}

// Resolve the actual JVM module rather than accepting source/project substitution.
tasks.register("verifyBindingConsumer") {
    dependsOn("compileKotlinJvm")
    doLast {
        val binding = configurations.getByName("jvmCompileClasspath").incoming.artifacts.artifacts.filter {
            (it.id.componentIdentifier as? ModuleComponentIdentifier)?.group == "io.maryk.foundationdb"
        }
        check(binding.size == 1) { "Expected one published FoundationDB JVM artifact, got $binding" }
        val component = binding.single().id.componentIdentifier as ModuleComponentIdentifier
        check(component.module == "foundationdb-multiplatform-jvm") { "Unexpected module: $component" }
        check(component.version == providers.gradleProperty("bindingVersion").get()) { "Unexpected version: $component" }
    }
}
