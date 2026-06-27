plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
}

// Static analysis — run `./gradlew detekt` to catch unused imports/code & potential bugs.
// Reports only (ignoreFailures): never breaks the build; tuned config silences Compose noise.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
}
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    ignoreFailures = true
    reports {
        html.required.set(true)
        txt.required.set(true)
    }
}

android {
    namespace = "com.receegpsstamp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.receegpsstamp"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = "1.5.4"
    }

    signingConfigs {
        create("release") {
            val ksFile = file("keystore.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String ?: ""
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String ?: ""
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ksFile = file("keystore.jks")
            signingConfig = if (ksFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true; buildConfig = true }

    packaging {
        resources { excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt") }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Firebase — auth (Google + phone-OTP), Firestore (recce data) & Storage (photos)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Full Guava on the COMPILE classpath. Firestore pulls it in (via gRPC) but only at runtime;
    // CameraX exposes ListenableFuture in its public API, so without this the app can't compile
    // against CameraScreen's camera calls ("Cannot access class ListenableFuture"). Pinned to match
    // Firestore's transitive Guava (32.1.3-android) to avoid version drift.
    implementation("com.google.guava:guava:32.1.3-android")

    // Google sign-in + location
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Local JSON store
    implementation("com.google.code.gson:gson:2.11.0")

    // CameraX
    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // PDF — direct JPEG (DCTDecode) embedding → small, HD report files (built-in PdfDocument stores lossless)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Accompanist permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Unit tests (pure JVM logic — no device needed)
    testImplementation("junit:junit:4.13.2")
}
