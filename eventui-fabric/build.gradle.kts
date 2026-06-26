plugins {
    id("fabric-loom")
    id("com.gradleup.shadow") version "9.4.2"
}


val shadowMe: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.18.2")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.108.0+1.21.1")

    implementation(project(":eventui-core"))
    include(project(":eventui-core"))

    implementation(project(":eventui-common"))
    include(project(":eventui-common"))

    shadowMe("org.yaml:snakeyaml:2.2")


    shadowMe("net.kyori:adventure-text-minimessage:4.17.0")
    shadowMe("net.kyori:adventure-text-serializer-gson:4.17.0")
    shadowMe("net.kyori:adventure-text-serializer-legacy:4.17.0")
}

tasks.shadowJar {
    configurations.set(listOf(shadowMe))
    relocate("net.kyori", "com.eventui.libs.kyori")
    relocate("org.yaml.snakeyaml", "com.eventui.libs.snakeyaml")
    archiveClassifier.set("dev-shadow")
    exclude("META-INF/maven/**")
    exclude("META-INF/INDEX.LIST")
}


tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    archiveClassifier.set("")
}


tasks.jar {
    archiveClassifier.set("dev")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

java {
    withSourcesJar()
}
