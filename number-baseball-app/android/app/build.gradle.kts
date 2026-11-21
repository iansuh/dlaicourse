import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Release values come only from Gradle properties (-P or ~/.gradle/gradle.properties) or environment variables.
fun config(name: String): String? =
    (providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull)
        ?.trim()?.takeIf { it.isNotEmpty() }

// Google's public AdMob test IDs: https://developers.google.com/admob/android/test-ads
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

val releaseValues = listOf("NB_SERVER_URL", "NB_CONTACT_EMAIL", "NB_ADMOB_APP_ID", "NB_ADMOB_BANNER_ID", "NB_ADMOB_INTERSTITIAL_ID")
    .associateWith { config(it) }
val missingReleaseValues = releaseValues.filterValues { it == null }.keys.toList()
// CI builds an unsigned release bundle without real values to check R8 and resource shrinking.
val placeholderRelease = config("NB_ALLOW_PLACEHOLDER_RELEASE") == "true"
val signingValues = listOf("NB_RELEASE_STORE_FILE", "NB_RELEASE_STORE_PASSWORD", "NB_RELEASE_KEY_ALIAS", "NB_RELEASE_KEY_PASSWORD")
    .associateWith { config(it) }
val releaseSigningConfigured = signingValues.values.all { it != null }

// Owner decision: minimum age 14 (Korean PIPA). Max ad content rating must not exceed the IARC rating (expected G).
val minimumAge = config("NB_MINIMUM_AGE")?.toInt() ?: 14
val maxAdContentRating = (config("NB_MAX_AD_CONTENT_RATING") ?: "G").also {
    require(it in setOf("G", "PG", "T", "MA")) { "NB_MAX_AD_CONTENT_RATING must be G, PG, T or MA" }
}

android {
    namespace = "app.numbaseball"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.numbaseball"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("int", "MINIMUM_AGE", minimumAge.toString())
        buildConfigField("String", "MAX_AD_CONTENT_RATING", "\"$maxAdContentRating\"")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(signingValues.getValue("NB_RELEASE_STORE_FILE")!!)
                storePassword = signingValues["NB_RELEASE_STORE_PASSWORD"]
                keyAlias = signingValues["NB_RELEASE_KEY_ALIAS"]
                keyPassword = signingValues["NB_RELEASE_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the host machine seen from the emulator; override with -PNB_SERVER_URL=...
            buildConfigField("String", "SERVER_URL", "\"${config("NB_SERVER_URL") ?: "http://10.0.2.2:8080"}\"")
            buildConfigField("String", "CONTACT_EMAIL", "\"${config("NB_CONTACT_EMAIL") ?: "support@example.com"}\"")
            manifestPlaceholders["admobAppId"] = testAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$testInterstitialId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned bundle when no signing values are present.
            signingConfig = signingConfigs.findByName("release")
            // Placeholders below are only reachable when checkReleaseConfig allows them (see that task).
            buildConfigField("String", "SERVER_URL", "\"${releaseValues["NB_SERVER_URL"] ?: "https://placeholder.invalid"}\"")
            buildConfigField("String", "CONTACT_EMAIL", "\"${releaseValues["NB_CONTACT_EMAIL"] ?: "support@example.com"}\"")
            manifestPlaceholders["admobAppId"] = releaseValues["NB_ADMOB_APP_ID"] ?: testAdMobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${releaseValues["NB_ADMOB_BANNER_ID"] ?: testBannerId}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${releaseValues["NB_ADMOB_INTERSTITIAL_ID"] ?: testInterstitialId}\"")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = true
        // Versions are pinned on purpose (compileSdk 36 + AGP 8.13); upgrades are a deliberate decision.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
}

/**
 * App only, no website (README): the terms and privacy policy are shown inside the app. Their public sections are taken
 * from docs/play (the release lead's single source of truth) at build time as localized raw resources.
 */
abstract class LegalTextsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        sources.files.forEach { file ->
            val name = when (file.name) {
                "TERMS_OF_USE.md" -> "legal_terms"
                "PRIVACY_POLICY.md" -> "legal_privacy"
                else -> error("unexpected ${file.name}")
            }
            val lines = file.readLines()
            fun section(marker: String): String {
                val start = lines.indexOfFirst { it.startsWith("## ") && marker in it }
                check(start >= 0) { "${file.name}: no public section '$marker'" }
                val body = lines.drop(start + 1).takeWhile { it.trim() != "---" && !it.startsWith("## ") }
                return body.joinToString("\n").trim() + "\n"
            }
            File(out, "raw").apply { mkdirs() }.resolve("$name.md").writeText(section("English"))
            File(out, "raw-ko").apply { mkdirs() }.resolve("$name.md").writeText(section("한국어"))
        }
    }
}

val legalTexts = tasks.register<LegalTextsTask>("generateLegalTexts") {
    sources.from(layout.projectDirectory.file("../../docs/play/TERMS_OF_USE.md"), layout.projectDirectory.file("../../docs/play/PRIVACY_POLICY.md"))
    outputDir.set(layout.buildDirectory.dir("generated/legal/res"))
}

androidComponents {
    onVariants { variant -> variant.sources.res?.addGeneratedSourceDirectory(legalTexts, LegalTextsTask::outputDir) }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "3g"
}

/** Stops a release build that would ship placeholders: real server URL and AdMob IDs are required. */
val checkReleaseConfig by tasks.registering {
    group = "verification"
    description = "Fails release builds that lack the real server URL / AdMob IDs or would sign placeholders."
    val missing = missingReleaseValues
    val placeholdersAllowed = placeholderRelease
    val signed = releaseSigningConfigured
    doLast {
        if (missing.isNotEmpty() && !placeholdersAllowed) {
            throw GradleException(
                "Release build needs ${missing.joinToString()}. Set them as Gradle properties or environment " +
                    "variables (see android/README.md). For an unsigned CI check bundle set NB_ALLOW_PLACEHOLDER_RELEASE=true.",
            )
        }
        if (missing.isNotEmpty() && signed) {
            throw GradleException("Refusing to sign a release that uses placeholder values for ${missing.joinToString()}.")
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseConfig) }

dependencies {
    implementation(libs.numbaseball.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.play.services.ads)
    implementation(libs.google.ump)
    implementation(libs.play.age.signals)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
}
