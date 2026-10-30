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

val localProperties = java.util.Properties().apply {
    val propertiesFile = file("local.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}
val rustlsVerifierMaven = System.getenv("YAQMC_RUSTLS_VERIFIER_MAVEN_DIR")
    ?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty("yaqmc.rustlsVerifierMavenDir")
require(!rustlsVerifierMaven.isNullOrBlank()) {
    "Rust TLS Maven repository is not configured; run npm run android:studio -- <Studio executable> --prepare-only at the repository root, then sync Gradle again"
}
dependencyResolutionManagement.repositories.maven {
    name = "rustlsPlatformVerifier"
    url = uri(rustlsVerifierMaven)
}
