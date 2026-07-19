import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    `maven-publish`
    signing
}

kotlin {
    explicitApi()
    jvmToolchain(24)

    // The Android target is configured through the Android-KMP library plugin's nested
    // `android {}` block instead of the deprecated com.android.library plugin + a top-level
    // `android {}` block + `androidTarget()`. This plugin publishes a single Android variant,
    // so `publishLibraryVariants(...)` is no longer needed.
    android {
        namespace = "com.collectiveidea.oauth"
        compileSdk = 36
        minSdk = 23

        // Opt in to JVM host unit tests so commonTest still runs on the Android/JVM host,
        // as `:oauth-core:testAndroidHostTest`.
        withHostTestBuilder {}
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
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

// Compile with the JDK 24 toolchain but emit Java 11 bytecode, so consumers only need a
// JDK 11+ toolchain to build against the published library. (The toolchain stays at 24
// because some dependencies ship Java 17 bytecode that an older toolchain can't read.)
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
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
