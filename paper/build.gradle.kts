plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    compileOnly(libs.paper.api)

    implementation(project(":api"))
    implementation(project(":core"))
}

tasks {
    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }

    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveBaseName.set("Glade")
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }
}
