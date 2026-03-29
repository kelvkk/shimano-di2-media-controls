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

android {
    namespace = "com.di2media"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.di2media"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        named("debug") {
            // Use default debug keystore at ~/.android/debug.keystore
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
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
