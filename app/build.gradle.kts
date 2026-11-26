plugins { id("com.android.application") }

android {
    namespace = "com.joelau.notifyisland"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.joelau.notifyisland"
        minSdk = 35
        targetSdk = 36
        versionCode = 34
        versionName = "0.1.33"
    }
    buildTypes {
        release {
            // Keep the verified module classes intact across LSPosed class loaders.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging.resources { merges += "META-INF/xposed/*"; excludes += "**" }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}


