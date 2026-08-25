import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.androidx.room)
}

// Release signing reads a file that is deliberately not in the repository.
// Without it the config is still created but left empty, so a debug build
// works and a release build is the one that fails - the right way round
// for a key nobody else should have.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun gitVersion(): String {
    val hash = providers.exec {
        commandLine(
            "git",
            "rev-parse",
            "--short=8",
            "HEAD"
        )
    }.standardOutput.asText.get().trim()

    val dirty = providers.exec {
        commandLine(
            "git",
            "status",
            "--porcelain"
        )
    }.standardOutput.asText.get().trim().isNotEmpty()

    return if (dirty) "$hash-dirty" else hash
}

// Stamped once at build time as an absolute instant (epoch millis), so the
// app can render it in whatever timezone the device is actually in.
fun buildTimeEpochMillis(): Long = System.currentTimeMillis()

// The Help screen reads docs/quickstart.md rather than a copy of it.
//
// User documentation wants to exist in three places at once: in the
// repository, behind a link in the README, and in front of somebody who
// has just been refused a permission and has no browser open. Three
// copies is three chances to answer the same question differently, so
// there is one file and the build carries it into the app.
//
// It lands in a generated resource directory rather than in src/main/res,
// so nothing tempts anyone to edit the copy instead of the original.
//
// Registered through the variant API rather than as a plain Copy on a
// source set: AGP 9 refuses a Provider there, because it can't tell
// generated files from ones a person is meant to edit. This way the task
// dependency is carried for us, and Studio knows the directory is
// read-only.
abstract class CopyQuickstart : DefaultTask() {

    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val raw = outputDir.get().asFile.resolve("raw").apply { mkdirs() }
        source.get().asFile.copyTo(raw.resolve("help.md"), overwrite = true)
    }
}

val copyQuickstart = tasks.register<CopyQuickstart>("copyQuickstart") {
    description = "Copies docs/quickstart.md in as the Help screen's text."
    source.set(rootProject.file("docs/quickstart.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            copyQuickstart,
            CopyQuickstart::outputDir
        )
    }
}

android {
    namespace = "org.fossridemeter.app"
    compileSdk = 37

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "org.fossridemeter.app"
        minSdk = 26
        targetSdk = 37
        // versionCode has to increase on every published build - Android
        // refuses an install whose code is not higher than the one on the
        // phone. 1 was 0.1.0.
        versionCode = 2
        versionName = "0.1.1"

        buildConfigField(
            "String",
            "GIT_VERSION",
            "\"${gitVersion()}\""
        )

        buildConfigField(
            "long",
            "BUILD_TIME",
            "${buildTimeEpochMillis()}L"
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Debug builds are signed with the release key when it is
            // available, so a debug build and a published APK install
            // over each other and the rides survive the swap.
            //
            // Android refuses an install whose signature differs from
            // the one already on the device, and the only way past that
            // refusal is an uninstall, which deletes the database. With
            // the default debug key that made every trip between a test
            // build and the real one cost a phone's history - and it is
            // not one key against another, it is three: Android Studio
            // signs with ~/.android/debug.keystore, a Gradle invocation
            // that cannot read that file generates its own, so even two
            // debug builds of the same commit could refuse each other.
            //
            // Falls back to the generated debug key when
            // keystore.properties isn't there, so a clone of this
            // repository still builds.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        release {
            signingConfig = signingConfigs.getByName("release")

            // R8 on: shrink, optimize, obfuscate. The rules this app
            // needs are in proguard-rules.pro and are short, because the
            // libraries that need rules ship their own.
            optimization {
                enable = true
            }

            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Where Room writes the exported schema JSON, one file per SCHEMA_VERSION.
// These are committed: a Migration can only be written or tested against a
// schema that was recorded, and the version before a change is the half that
// is otherwise gone by the time it is needed. The Room plugin (rather than
// the room.schemaLocation KSP argument) also hands this directory to
// androidTest as an asset directory, which is what MigrationTestHelper reads.
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // BOM must be declared first (or at least on the same configurations)
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)   // gives you the Flow<List<RideRecord>> support in RideDao
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.zxing.core)   // QR encoding only; pure Java, no Android or gms dependency

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
