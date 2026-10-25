plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.yaqmc.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.yaqmc.android.nativeui"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("YAQMC_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("YAQMC_VERSION_NAME") ?: "0.1.1"
        buildConfigField("String", "YAQMC_BUILD_COMMIT", "\"${System.getenv("YAQMC_BUILD_COMMIT") ?: "unknown"}\"")
        // Normal installable builds remain ARM64. CI's x86_64 emulator uses a matching JNI ABI.
        val nativeAbi = System.getenv("YAQMC_ANDROID_NATIVE_ABI") ?: "arm64-v8a"
        require(nativeAbi in setOf("arm64-v8a", "x86_64")) { "Unsupported native ABI: $nativeAbi" }
        ndk { abiFilters += nativeAbi }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { versionNameSuffix = "-native-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../app/proguard-rules.pro")
        }
        // Optimized, locally installable comparison build. Never uses the production signing key.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            versionNameSuffix = "-native-benchmark"
        }
    }
    sourceSets["main"].apply {
        res.srcDir("../app/src/main/res")
        jniLibs.srcDir(System.getenv("YAQMC_ANDROID_NATIVE_LIB_DIR") ?: "../app/build/generated/jniLibs")
    }
    sourceSets["test"].resources.srcDir("../../../../packages/yaqmc-client/fixtures")
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

kotlin.sourceSets.getByName("main").kotlin.apply {
    srcDir("../app/src/main/java")
    exclude("**/MainActivity.kt", "**/plugin/**")
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
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
