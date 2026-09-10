plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "net.glademc.glade.plugin"
    version = "1.0.0-Alpha"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}