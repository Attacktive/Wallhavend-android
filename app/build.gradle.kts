import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
	id("com.google.dagger.hilt.android")
	id("com.google.devtools.ksp")
	id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
	val file = rootProject.file("local.properties")
	if (file.exists()) {
		file.inputStream()
			.use { load(it) }
	}
}

configure<ApplicationExtension> {
	namespace = "xyz.attacktive.wallhavend"
	compileSdk = 37

	defaultConfig {
		applicationId = "xyz.attacktive.wallhavend"
		minSdk = 26
		targetSdk = 37
		versionCode = 1
		versionName = "1.0.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	signingConfigs {
		create("release") {
			storeFile = file("../release.keystore")
			storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("KEYSTORE_PASSWORD")
			keyAlias = "Wallhavend"
			keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("KEY_PASSWORD")
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
	val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
	implementation(composeBom)
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.material:material-icons-extended")
	debugImplementation("androidx.compose.ui:ui-tooling")

	implementation("androidx.navigation:navigation-compose:2.9.8")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
	implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

	implementation("com.google.dagger:hilt-android:2.59.2")
	ksp("com.google.dagger:hilt-compiler:2.59.2")
	implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

	implementation("com.squareup.retrofit2:retrofit:3.0.0")
	implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
	implementation("com.squareup.okhttp3:okhttp:5.3.2")
	implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

	implementation("androidx.datastore:datastore-preferences:1.2.1")
	implementation("io.coil-kt:coil-compose:2.7.0")
	implementation("androidx.core:core-ktx:1.18.0")
	implementation("androidx.activity:activity-compose:1.13.0")

	testImplementation("junit:junit:4.13.2")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
	testImplementation("io.mockk:mockk:1.14.9")
	testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
	testImplementation("androidx.datastore:datastore-preferences-core:1.2.1")
	androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
