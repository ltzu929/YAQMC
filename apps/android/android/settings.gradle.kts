pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "yaqmc-android"
include(":nativeApp")

val rustlsVerifierMaven = System.getenv("YAQMC_RUSTLS_VERIFIER_MAVEN_DIR")
require(!rustlsVerifierMaven.isNullOrBlank()) {
    "YAQMC_RUSTLS_VERIFIER_MAVEN_DIR is required; invoke Gradle through scripts/run-android-gradle.mjs"
}
dependencyResolutionManagement.repositories.maven {
    name = "rustlsPlatformVerifier"
    url = uri(rustlsVerifierMaven)
}
