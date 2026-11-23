import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "io.appetise.shopper"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.browser:browser:1.6.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// The version comes from the release tag: `-PVERSION_NAME=0.1.0`.
mavenPublishing {
    coordinates("io.appetise", "shopper", providers.gradleProperty("VERSION_NAME").getOrElse("0.0.0-SNAPSHOT"))

    configure(
        AndroidSingleVariantLibrary(
            // Maven Central requires a javadoc jar.
            publishJavadocJar = true,
            sourcesJar = true,
            variant = "release",
        )
    )

    publishToMavenCentral(automaticRelease = true)

    // Unsigned locally, so publishToMavenLocal works without the release key.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set("Appetise Shopper")
        description.set("The Appetise shopper widget in Android apps, as a native sheet.")
        inceptionYear.set("2026")
        url.set("https://github.com/MenuAid/appetise-shopper-widget-mobile-sdk")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("appetise")
                name.set("Appetise")
                url.set("https://appetise.io")
            }
        }
        scm {
            url.set("https://github.com/MenuAid/appetise-shopper-widget-mobile-sdk")
            connection.set("scm:git:https://github.com/MenuAid/appetise-shopper-widget-mobile-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/MenuAid/appetise-shopper-widget-mobile-sdk.git")
        }
    }
}
