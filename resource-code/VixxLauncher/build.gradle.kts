
plugins {
    id("com.android.application")    id("org.jetbrains.kotlin.android") version "2.0.21"
}

val nameId = "com.movtery.zalithlauncher"
val vixxApplicationId = "com.vixx.launcher"
val generatedZalithDir = file("$buildDir/generated/source/zalith/java")
val launcherAPPName = project.findProperty("launcher_app_name") as? String ?: error("The \"launcher_app_name\" property is not set in gradle.properties.")
val launcherName = project.findProperty("launcher_name") as? String ?: error("The \"launcher_name\" property is not set in gradle.properties.")
val launcherVersionCode = (project.findProperty("launcher_version_code") as? String)?.toIntOrNull() ?: error("The \"launcher_version_code\" property is not set as an integer in gradle.properties.")
val launcherVersionName = project.findProperty("launcher_version_name") as? String ?: error("The \"launcher_version_name\" property is not set in gradle.properties.")

configurations {
    create("instrumentedClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

android {
    namespace = nameId
    compileSdk = 34


    defaultConfig {
        applicationId = vixxApplicationId
        minSdk = 26
        targetSdk = 34
        versionCode = launcherVersionCode
        versionName = launcherVersionName
        multiDexEnabled = true
        manifestPlaceholders["launcher_name"] = launcherAPPName
    }

    buildTypes {
        val storageProviderId = "$vixxApplicationId.storage_provider"

        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            resValue("string", "storageProviderAuthorities", "$storageProviderId.debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            resValue("string", "storageProviderAuthorities", storageProviderId)
        }
    }

    sourceSets["main"].java.srcDirs(generatedZalithDir)

    ndkVersion = "25.2.9519653"

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libbytehook.so")
        }
    }

    buildFeatures {
        prefab = true
        buildConfig = true
        viewBinding = true
    }

    buildToolsVersion = "34.0.0"
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

fun generateJavaClass(sourceOutputDir: File, packageName: String, className: String, constantMap: Map<String, String>) {
    val outputDir = File(sourceOutputDir, packageName.replace(".", "/"))
    outputDir.mkdirs()
    val javaFile = File(outputDir, "$className.java")
    fun escapeJavaString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    val constants = constantMap.entries.joinToString("\n") { (key, value) ->
        "\tpublic static final String $key = \"${escapeJavaString(value)}\";"
    }
    javaFile.writeText(
        """
        |/**
        | * Automatically generated file. DO NOT MODIFY
        | */
        |package $packageName;
        |
        |public class $className {
        |$constants
        |}
        """.trimMargin()
    )
    println("Generated Java file: ${javaFile.absolutePath}")
}

tasks.register("generateInfoDistributor") {
    doLast {
        val constantMap = mapOf(
            "LAUNCHER_NAME" to project.property("launcher_name").toString(),
            "APP_NAME" to project.property("launcher_app_name").toString(),
            "BUILD_TYPE" to "DEBUG",
            "CURSEFORGE_API_KEY" to (
                System.getenv("CURSEFORGE_API_KEY")
                    ?: (project.findProperty("curseforge_api_key") as? String)
                    ?: ""
            )
        )
        generateJavaClass(generatedZalithDir, "com.movtery.zalithlauncher", "InfoDistributor", constantMap)
    }
}

tasks.named("preBuild") {
    dependsOn("generateInfoDistributor")
}

dependencies {
    // Keep one Gson artifact; remove the duplicate local gson JAR from libs/.
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("commons-codec:commons-codec:1.17.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0-beta01")
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("com.github.duanhong169:checkerboarddrawable:1.0.2")
    implementation("com.github.PojavLauncherTeam:portrait-sdp:ed33e89cbc")
    implementation("com.github.PojavLauncherTeam:portrait-ssp:6c02fd739b")
    implementation("com.github.Mathias-Boulay:ExtendedView:1.0.0")
    implementation("com.github.Mathias-Boulay:android_gamepad_remapper:2.0.3")
    implementation("com.github.Mathias-Boulay:virtual-joystick-android:1.14")
    implementation("com.github.skydoves:powerspinner:1.2.7")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.angcyo.DslTablayout:TabLayout:3.6.5")

    implementation("top.fifthlight.touchcontroller:proxy-client-android:0.0.2")

    implementation("org.tukaani:xz:1.9")
    implementation("net.sourceforge.htmlcleaner:htmlcleaner:2.6.1")
    implementation("com.bytedance:bytehook:1.0.10")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.commonmark:commonmark:0.19.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    implementation("com.getkeepsafe.taptargetview:taptargetview:1.14.0")
    implementation("io.github.petterpx:floatingx:2.3.3")
    implementation("org.greenrobot:eventbus:3.3.1")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
}
