plugins {
	alias(libs.plugins.android.test)
}

android {
	namespace = "org.jellyfin.androidtv.benchmark"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = 24
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		create("benchmark") {
			isDebuggable = true
			signingConfig = getByName("debug").signingConfig
			matchingFallbacks += listOf("release")
		}
	}

	targetProjectPath = ":app"
	experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
	implementation(libs.androidx.test.ext.junit)
	implementation(libs.androidx.test.espresso.core)
	implementation(libs.androidx.test.uiautomator)
	implementation(libs.androidx.benchmark.macro.junit4)
}
