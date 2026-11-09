plugins { kotlin("multiplatform") version "2.4.10" }
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
