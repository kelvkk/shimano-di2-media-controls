plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val generateIcons by tasks.registering(Exec::class) {
    val src = rootProject.file("app-logo.png")
    val resDir = layout.projectDirectory.dir("src/main/res")
    inputs.file(src)
    outputs.dir(resDir.dir("mipmap-xxxhdpi"))

    val densities = mapOf("mdpi" to (48 to 108), "hdpi" to (72 to 162), "xhdpi" to (96 to 216), "xxhdpi" to (144 to 324), "xxxhdpi" to (192 to 432))
    val cmds = densities.flatMap { (density, sizes) ->
        val dir = resDir.dir("mipmap-$density").asFile
        listOf(
            "mkdir -p $dir",
            "magick $src -resize ${sizes.first}x${sizes.first} $dir/ic_launcher.png",
            "magick $src -resize ${sizes.second * 66 / 108}x${sizes.second * 66 / 108} -gravity center -background white -extent ${sizes.second}x${sizes.second} $dir/ic_launcher_foreground.png",
        )
    }
    commandLine("bash", "-c", cmds.joinToString(" && "))
}

tasks.named("preBuild") { dependsOn(generateIcons) }

// Release signing is injected by CI (GitHub Secrets) so the key never lives in the repo.
// Without it the build falls back to the default debug key (local builds).
val ciKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "com.di2media"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.di2media"
        minSdk = 26
        targetSdk = 34
        // CI run number keeps increasing, so every build can upgrade the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 3
        versionName = "20261004v3"
    }

    signingConfigs {
        named("debug") {
            // Use default debug keystore at ~/.android/debug.keystore
        }
        if (ciKeystorePath != null) {
            create("ci") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (ciKeystorePath != null) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (ciKeystorePath != null) "ci" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
