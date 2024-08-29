plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
}

fun gradlePropertyOrEnvironmentVariable(name: String) = (project.findProperty(name) ?: System.getenv(name)) as? String

val signingKeyAsciiArmored = gradlePropertyOrEnvironmentVariable("SIGNING_KEY")
val signingKeyPassword = gradlePropertyOrEnvironmentVariable("SIGNING_PASSWORD")
if (signingKeyAsciiArmored != null) {
    subprojects {
        plugins.withType<SigningPlugin> {
            configure<SigningExtension> {
                useInMemoryPgpKeys(signingKeyAsciiArmored, signingKeyPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}

val sonatypeUsername = gradlePropertyOrEnvironmentVariable("SONATYPE_USERNAME")
val sonatypePassword = gradlePropertyOrEnvironmentVariable("SONATYPE_PASSWORD")
if (sonatypeUsername != null) {
    subprojects {
        plugins.withType<MavenPublishPlugin>() {
            configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "oss"
                        val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                        val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                        url = if (version.toString().endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

                        credentials {
                            username = sonatypeUsername
                            password = sonatypePassword
                        }
                    }
                }
            }
        }
    }
}
