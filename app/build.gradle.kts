import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.hilt.android)
	alias(libs.plugins.ksp)
	alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
	val file = rootProject.file("local.properties")
	if (file.exists()) {
		file.inputStream()
			.use { load(it) }
	}
}

val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("KEYSTORE_PASSWORD")

configure<ApplicationExtension> {
	namespace = "xyz.attacktive.wallhavend"
	compileSdk = 37

	defaultConfig {
		applicationId = "xyz.attacktive.wallhavend"
		minSdk = 26
		targetSdk = 37
		versionCode = 8
		versionName = "1.0.7"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	signingConfigs {
		create("release") {
			storeFile = file("../release.keystore")
			storePassword = keystorePassword
			keyAlias = "wallhavend"
			keyPassword = keystorePassword
		}
	}

	buildTypes {
		release {
			signingConfig = signingConfigs.getByName("release")
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
	}
}

dependencies {
	val composeBom = platform(libs.compose.bom)
	implementation(composeBom)
	implementation(libs.compose.ui)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.compose.material3)
	implementation(libs.compose.material.icons.extended)
	debugImplementation(libs.compose.ui.tooling)

	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)

	implementation(libs.hilt.android)
	ksp(libs.hilt.compiler)
	implementation(libs.androidx.hilt.navigation.compose)

	implementation(libs.retrofit)
	implementation(libs.retrofit.kotlinx.serialization.converter)
	implementation(libs.okhttp)
	implementation(libs.okhttp.logging.interceptor)
	implementation(libs.kotlinx.serialization.json)

	implementation(libs.androidx.datastore.preferences)
	implementation(libs.coil.compose)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.mockk)
	testImplementation(libs.okhttp.mockwebserver)
	testImplementation(libs.androidx.datastore.preferences.core)
	androidTestImplementation(libs.androidx.junit)
}
