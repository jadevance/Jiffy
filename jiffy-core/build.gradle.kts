import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.vanniktech.maven.publish")
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.13")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates("io.github.jadevance", "jiffy-core", project.version.toString())

    pom {
        name.set("Jiffy")
        description.set("A Java port of Spiffy.Monitoring — structured logging for the JVM with Spiffy-shaped output for cross-stack Splunk fluency.")
        inceptionYear.set("2026")
        url.set("https://github.com/jadevance/Jiffy")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("jadevance")
                name.set("Jade Vance")
                url.set("https://github.com/jadevance")
            }
        }
        scm {
            url.set("https://github.com/jadevance/Jiffy")
            connection.set("scm:git:git://github.com/jadevance/Jiffy.git")
            developerConnection.set("scm:git:ssh://git@github.com/jadevance/Jiffy.git")
        }
    }
}
