plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.effect.browser"
    // GeckoView 155 requires compiling against 37.1+; 37.2 is the current stable platform.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "io.effect.browser"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // GeckoView + the tor binaries are the two things that dominate APK size.
        // Shipping per-ABI splits keeps each install close to a third of the universal APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Release signing comes from Gradle properties, never from the repo. Pass
    // -PRELEASE_STORE_FILE=... -PRELEASE_STORE_PASSWORD=... -PRELEASE_KEY_ALIAS=... \
    // -PRELEASE_KEY_PASSWORD=... on the command line (or a local, gitignored
    // gradle.properties) to sign a release build. Without them, release stays unsigned,
    // same as stock AGP behaviour.
    val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
    val hasReleaseSigning = releaseStoreFile != null

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/versions/9/previous-compilation-data.bin",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Used directly to hand-initialise kmp-tor in the :tor process (see TorStartup).
    implementation(libs.androidx.startup.runtime)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.geckoview)

    testImplementation(libs.junit)

    implementation(libs.kmptor.runtime.service)
    // noexec, not exec: the exec variant runs the tor binary as a child process, which forces
    // android:extractNativeLibs=true on the whole APK -- and that would unpack GeckoView's
    // ~200MB libxul.so on install too. The noexec variant loads tor via JNI instead.
    implementation(libs.kmptor.resource.noexec)
}
