import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("fabric-loom") version "1.16-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("lasseclient") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    mavenCentral()
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.teamresourceful.com/repository/maven-public/")
    // Hypixel Mod API core library (packet definitions + protocol).
    maven("https://repo.hypixel.net/repository/Hypixel/")
    // Modrinth maven — hosts the official Hypixel Mod API Fabric implementation mod.
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Resourceful Config — annotation-based config + in-game/Mod Menu screen.
    // Bundled into the jar via Loom `include` so users don't need a separate download.
    val resourcefulConfig = "com.teamresourceful.resourcefulconfig:resourcefulconfig-fabric-${project.property("minecraft_version")}:${project.property("resourcefulconfig_version")}"
    modImplementation(resourcefulConfig)
    include(resourcefulConfig)

    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

    // Hypixel Mod API — official client<->server plugin-message protocol.
    //
    // Two pieces: the platform-agnostic CORE (`net.hypixel:mod-api`, packet defs + protocol)
    // and the official Fabric IMPLEMENTATION mod (transport + the `hypixel-mod-api` modid).
    // Using the official impl is the recommended path: a custom transport can "fight" other
    // mods over the event-packet version registration and trigger Hypixel's conflict warnings.
    //
    // The core must be a real runtime dependency, not `compileOnly`: the Fabric impl bundles
    // its own core via jar-in-jar, but JIJ is NOT unpacked in the dev runtime, so without this
    // both our code and the impl hit NoClassDefFoundError on `HypixelModAPI` in `runClient`.
    // We also `include` the core so the shipped jar is self-contained.
    val hypixelCore = "net.hypixel:mod-api:1.0.1"
    implementation(hypixelCore)
    include(hypixelCore)
    val hypixelModApi = "maven.modrinth:hypixel-mod-api:1.0.1+build.1+mc1.21"
    modImplementation(hypixelModApi)
    include(hypixelModApi)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    inputs.property("fabric_version", project.property("fabric_version"))
    inputs.property("resourcefulconfig_version", project.property("resourcefulconfig_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version.toString(),
            "minecraft_version" to project.property("minecraft_version")!!,
            "loader_version" to project.property("loader_version")!!,
            "kotlin_loader_version" to project.property("kotlin_loader_version")!!,
            "fabric_version" to project.property("fabric_version")!!,
            "resourcefulconfig_version" to project.property("resourcefulconfig_version")!!,
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
    }
}
