import groovy.json.JsonSlurper
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.tasks.GradleBuild

val consumerWorkspace = layout.buildDirectory.dir("external-consumer-check")
val bindingVersion = project.version.toString()
val consumerModes = listOf(
    Triple("Root", "foundationdb-multiplatform", false),
    Triple("Jvm", "foundationdb-multiplatform-jvm", false),
    Triple("Pom", "foundationdb-multiplatform-jvm", true),
)

val prepareExternalConsumers = tasks.register("prepareExternalConsumers") {
    dependsOn(
        "allMetadataJar", "jvmJar",
        "generateMetadataFileForJvmPublication", "generatePomFileForJvmPublication",
        "generateMetadataFileForKotlinMultiplatformPublication",
        "generatePomFileForKotlinMultiplatformPublication",
    )
    inputs.dir(layout.projectDirectory.dir("verification/consumer"))
    doLast {
        val workspace = consumerWorkspace.get().asFile
        check(!workspace.exists() || workspace.deleteRecursively()) { "Cannot clear consumer workspace" }
        check(workspace.mkdirs()) { "Cannot create consumer workspace" }
        val repository = workspace.resolve("repository")
        for (publication in listOf("kotlinMultiplatform", "jvm")) {
            val generated = layout.buildDirectory.dir("publications/$publication").get().asFile
            val pomFile = generated.resolve("pom-default.xml")
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val pom = factory.newDocumentBuilder().parse(pomFile)
            fun field(name: String): String = pom.getElementsByTagNameNS(
                "http://maven.apache.org/POM/4.0.0", name,
            ).item(0).textContent
            val group = field("groupId")
            val module = field("artifactId")
            val version = field("version")
            check(version == bindingVersion) { "Consumer publication versions differ" }
            val destination = repository.resolve("${group.replace('.', '/')}/$module/$version")
            check(destination.mkdirs()) { "Cannot create artifact directory" }
            pomFile.copyTo(destination.resolve("$module-$version.pom"))
            val metadataFile = generated.resolve("module.json")
            metadataFile.copyTo(destination.resolve("$module-$version.module"))
            val metadata = JsonSlurper().parse(metadataFile) as Map<*, *>
            for (variant in metadata["variants"] as List<*>) {
                val files = (variant as Map<*, *>)["files"] as? List<*> ?: emptyList<Any>()
                for (entry in files) {
                    val artifact = entry as Map<*, *>
                    val name = artifact["name"] as String
                    val source = layout.buildDirectory.file("libs/$name").get().asFile
                    val digest = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
                        .joinToString("") { "%02x".format(it) }
                    check(digest == artifact["sha256"]) { "Artifact checksum differs: $name" }
                    source.copyTo(destination.resolve(artifact["url"] as String), overwrite = true)
                }
            }
        }
        for ((mode, _, _) in consumerModes) {
            check(layout.projectDirectory.dir("verification/consumer").asFile.copyRecursively(
                workspace.resolve("consumers/${mode.lowercase()}"),
            )) { "Cannot copy independent consumer fixture" }
        }
    }
}

// GradleBuild runs independent builds with their own settings and repositories.
// They are deliberately not included builds: project substitution would hide publication defects.
val externalConsumerChecks = consumerModes.map { (mode, coordinate, pomOnly) ->
    tasks.register<GradleBuild>("checkExternalConsumer$mode") {
        dependsOn(prepareExternalConsumers)
        dir = consumerWorkspace.get().dir("consumers/${mode.lowercase()}").asFile
        buildName = "foundationdb-consumer-${mode.lowercase()}"
        tasks = listOf("verifyBindingConsumer")
        startParameter.projectProperties = mapOf(
            "bindingRepository" to consumerWorkspace.get().dir("repository").asFile.absolutePath,
            "bindingCoordinate" to coordinate,
            "bindingVersion" to bindingVersion,
            "pomOnly" to pomOnly.toString(),
        )
        doLast { logger.lifecycle("External Gradle consumer passed: $coordinate (POM only: $pomOnly)") }
    }
}
externalConsumerChecks.zipWithNext { previous, next -> next.configure { mustRunAfter(previous) } }

tasks.register("checkExternalConsumer") {
    group = "verification"
    description = "Compile independent Gradle consumers against generated Maven artifacts."
    dependsOn(externalConsumerChecks)
}
