plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

group = "com.eventui"
version = "1.0.1"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    implementation(project(":eventui-common"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.2")
    compileOnly("net.kyori:adventure-api:4.17.0")
}

tasks.shadowJar {
    relocate("net.kyori.adventure.text.minimessage", "com.eventui.libs.minimessage")
    relocate("net.kyori.adventure.platform",         "com.eventui.libs.adventure.platform")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
