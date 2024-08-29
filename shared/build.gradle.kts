plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
    signing
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "OAuthKMP"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.collectiveidea.oauth"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar.get())

        pom {
            name.set(artifactId)
            description.set("A Kotlin Multiplatform library for Android and iOS that provides an OAuth PKCE flow implementation.")

            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            url.set("https://github.com/collectiveidea/oauth-kmp")

            issueManagement {
                system.set("Github")
                url.set("https://github.com/collectiveidea/oauth-kmp/issues")
            }

            scm {
                connection.set("https://github.com/collectiveidea/oauth-kmp.git")
                url.set("https://github.com/collectiveidea/oauth-kmp")
            }

            developers {
                developer {
                    id.set("collectiveidea")
                    name.set("Collective Idea")
                    url.set("https://github.com/collectiveidea")
                }
            }
        }
    }
}