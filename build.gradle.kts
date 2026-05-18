plugins {
    id("com.vanniktech.maven.publish") version "0.28.0" apply false
}

allprojects {
    group = "io.github.jadevance"
    version = "0.1.2-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
