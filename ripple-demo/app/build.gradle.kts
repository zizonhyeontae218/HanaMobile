plugins {
    id("com.android.application")
}

android {
    namespace = "com.openai.rippledemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openai.rippledemo"
        minSdk = 33
        targetSdk = 35
        versionCode = 4
        versionName = "0.4-lockscreen"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
