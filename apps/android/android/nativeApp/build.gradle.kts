import groovy.json.JsonSlurper
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

val repositoryRoot = rootProject.file("../../..")
val localProperties = Properties().apply {
  rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun localSetting(property: String, environment: String): String? =
  providers.gradleProperty(property).orNull
    ?: System.getenv(environment)?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty(property)?.takeIf { it.isNotBlank() }

// Studio and command-line Gradle read the same version, without a launcher environment.
val packageJson = JsonSlurper().parse(repositoryRoot.resolve("package.json")) as Map<*, *>
val appVersion =
  providers.gradleProperty("yaqmc.versionName").orNull ?: packageJson["version"].toString()
val versionParts =
  Regex("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-(alpha|beta|rc)\\.(0|[1-9]\\d*))?")
    .matchEntire(appVersion)?.groupValues ?: error("Invalid Android version: $appVersion")
val (major, minor, patch) = versionParts.drop(1).take(3).map { it.toInt() }
val stage = mapOf("alpha" to 1, "beta" to 2, "rc" to 3, "" to 9).getValue(versionParts[4])
val serial = versionParts[5].toIntOrNull() ?: 0
require(major <= 20 && minor <= 99 && patch <= 99 && serial <= 999)
val appVersionCode =
  major * 100_000_000 + minor * 1_000_000 + patch * 10_000 + stage * 1_000 + serial
require(appVersionCode <= 2_100_000_000)
val buildCommit = providers.exec {
  workingDir(repositoryRoot)
  commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.get().trim()
require(buildCommit.matches(Regex("[0-9a-f]{40}"))) { "Cannot determine source commit" }

// The standard Android signing properties stay private on this machine.
val keystoreProperties = Properties().apply {
  rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(property: String, environment: String): String? =
  keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
    ?: System.getenv(environment)?.takeIf { it.isNotBlank() }

val nativeAbi = localSetting("yaqmc.nativeAbi", "YAQMC_ANDROID_NATIVE_ABI") ?: "arm64-v8a"
require(nativeAbi in setOf("arm64-v8a", "x86_64")) { "Unsupported native ABI: $nativeAbi" }
val offlineUiTests = localSetting("yaqmc.offlineUiTests", "YAQMC_ANDROID_OFFLINE_UI_TESTS") == "1"
val prebuiltCore = localSetting("yaqmc.nativeLibDir", "YAQMC_ANDROID_NATIVE_LIB_DIR")
val nativeDirectory =
  prebuiltCore?.let { file(it) } ?: layout.buildDirectory.dir("generated/jniLibs").get().asFile

android {
  namespace = "org.yaqmc.android"
  compileSdk = 36
  ndkVersion = "28.2.13676358"
  defaultConfig {
    applicationId = "org.yaqmc.android"
    minSdk = 26
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersion
    buildConfigField("String", "YAQMC_BUILD_COMMIT", "\"$buildCommit\"")
    // Normal installable builds remain ARM64. Local x86_64 emulators use a matching JNI ABI.
    ndk { abiFilters += nativeAbi }
    testInstrumentationRunner = if (offlineUiTests)
      "org.yaqmc.android.nativeui.NativeOfflineTestRunner"
    else "androidx.test.runner.AndroidJUnitRunner"
  }
  signingConfigs {
    create("release") {
      storeFile =
        signingValue("storeFile", "ANDROID_RELEASE_KEYSTORE")?.let { rootProject.file(it) }
      storePassword = signingValue("storePassword", "ANDROID_RELEASE_STORE_PASSWORD")
      keyAlias = signingValue("keyAlias", "ANDROID_RELEASE_KEY_ALIAS")
      keyPassword = signingValue("keyPassword", "ANDROID_RELEASE_KEY_PASSWORD")
    }
  }
  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-native-debug"
      manifestPlaceholders["appLabel"] = "YAQMC Debug"
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("release")
      manifestPlaceholders["appLabel"] = "YAQMC"
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    // Comparison builds are explicit development artifacts, separate from normal upgrades.
    create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      applicationIdSuffix = ".benchmark"
      manifestPlaceholders["appLabel"] = "YAQMC Benchmark"
      matchingFallbacks += "release"
      versionNameSuffix = "-native-benchmark"
    }
  }
  sourceSets["main"].apply {
    jniLibs.srcDir(nativeDirectory)
  }
  sourceSets["test"].resources.srcDir("../../../../crates/yaqmc-protocol/fixtures")
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  kotlinOptions { jvmTarget = "21" }
  buildFeatures { compose = true; buildConfig = true }
  // Both in-app languages must be available offline, including in a future bundle build.
  bundle { language { enableSplit = false } }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Cargo/NDK remains necessary for Rust. Gradle owns the dependency so IDE Build also builds Core.
val buildRustCore = tasks.register<Exec>("buildRustCore") {
  group = "build"
  description = "Build the Rust Core for the selected Android ABI using cargo-ndk"
  onlyIf { prebuiltCore == null && !offlineUiTests }
  workingDir(repositoryRoot)
  val cargoHome = localSetting("yaqmc.cargoHome", "CARGO_HOME")
  val rustupHome = localSetting("yaqmc.rustupHome", "RUSTUP_HOME")
  val cargo = cargoHome?.let {
    "$it/bin/cargo" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
  } ?: "cargo"
  commandLine(
    cargo, "ndk", "-P", "26", "-t", nativeAbi, "-o", nativeDirectory.absolutePath,
    "build", "--locked", "-p", "yaqmc-android", "--release"
  )
  cargoHome?.let {
    environment("CARGO_HOME", it)
    environment("PATH", "$it/bin${File.pathSeparator}${System.getenv("PATH")}")
  }
  rustupHome?.let { environment("RUSTUP_HOME", it) }
  val sdk = android.sdkDirectory.absolutePath
  environment("ANDROID_HOME", sdk)
  environment("ANDROID_NDK_HOME", "$sdk/ndk/${android.ndkVersion}")
  inputs.files(repositoryRoot.resolve("Cargo.toml"), repositoryRoot.resolve("Cargo.lock"))
  inputs.files(fileTree(repositoryRoot.resolve("crates")) { include("**/*.rs", "**/Cargo.toml") })
  inputs.property("abi", nativeAbi)
  outputs.file(nativeDirectory.resolve("$nativeAbi/libyaqmc_core.so"))
}
tasks.named("preBuild") { dependsOn(buildRustCore) }
tasks.matching { it.name == "mergeReleaseNativeLibs" }.configureEach {
  doFirst {
    require(!offlineUiTests && nativeAbi == "arm64-v8a") { "Release requires the ARM64 Rust Core" }
    require(nativeDirectory.resolve("arm64-v8a/libyaqmc_core.so").isFile) { "ARM64 Rust Core is missing" }
  }
}
tasks.matching { it.name == "validateSigningRelease" }.configureEach {
  doFirst {
    val signing = android.signingConfigs.getByName("release")
    val store = signing.storeFile
      ?: error("Configure the existing key in keystore.properties before building release")
    val keyStore = KeyStore.getInstance(store, signing.storePassword?.toCharArray())
    val certificate =
      keyStore.getCertificate(signing.keyAlias) ?: error("Release signing certificate is missing")
    val sha256 = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
      .joinToString("") { "%02x".format(it) }
    require(sha256 == "2ca3123c8223156ed5e40de5c90299a43a354a8ca0ef1ce75a029e68e345097b") {
      "Release signing key does not match the existing YAQMC installation"
    }
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.exifinterface:exifinterface:1.4.1")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation(platform("androidx.compose:compose-bom:2025.10.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
  implementation("androidx.media3:media3-common:1.10.1")
  implementation("androidx.media3:media3-session:1.10.1")
  implementation("io.coil-kt.coil3:coil-compose:3.3.0")
  implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
  implementation("rustls:rustls-platform-verifier:0.1.1")
  testImplementation(kotlin("test-junit"))
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20250517")
  androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  androidTestImplementation("androidx.test:runner:1.7.0")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
