import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
    signing
}

kotlin {
    explicitApi()

    androidTarget {
        publishAllLibraryVariants()
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
            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.krypt.csprng)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)

            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)

            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.androidx.browser)
        }
    }
}

android {
    namespace = "com.collectiveidea.oauth"
    compileSdk = 34
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
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

// Workaround for: https://github.com/gradle/gradle/issues/26091
tasks.withType<AbstractPublishToMaven>().configureEach {
    val signingTasks = tasks.withType<Sign>()
    mustRunAfter(signingTasks)
}
