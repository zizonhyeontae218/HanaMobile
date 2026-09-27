plugins {
    id("com.android.application")
}

android {
    namespace = "com.openai.rippledemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openai.rippledemo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-demo"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
