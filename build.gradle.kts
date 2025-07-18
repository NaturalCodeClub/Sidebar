plugins {
    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.ncc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.extendedclip.com/releases"){
        name = "helpchatRepo"
    }
}

dependencies {
    val scoreboardLibraryVersion = "2.4.1"
    implementation("net.megavex:scoreboard-library-api:${scoreboardLibraryVersion}")
    runtimeOnly("net.megavex:scoreboard-library-implementation:${scoreboardLibraryVersion}")
    implementation("net.megavex:scoreboard-library-extra-kotlin:${scoreboardLibraryVersion}")
    // Kotlin specific extensions (optional)
    // Add packet adapter implementations you want:
    runtimeOnly("net.megavex:scoreboard-library-modern:${scoreboardLibraryVersion}")
    compileOnly("me.clip:placeholderapi:2.11.6")

    compileOnly("dev.folia:folia-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
