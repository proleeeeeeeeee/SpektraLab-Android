plugins {
    id("com.android.library")
    kotlin("android")
}
val upstream = rootProject.file("third_party/spektralab")
val shaderCompiler = providers.gradleProperty("glslang")
    .orElse(providers.environmentVariable("GLSLANG_VALIDATOR"))
    .orElse("glslangValidator")
val engineAssets = layout.buildDirectory.dir("generated/engine-assets")
val shaderAssets = layout.buildDirectory.dir("generated/shader-assets")

android {
    namespace = "io.github.proleeeeeeeeee.spektralab.engine"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += providers.gradleProperty("androidAbi").orElse("arm64-v8a").get().split(",") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DSPEKTRALAB_GLSLANG=" + shaderCompiler.get(), "-DANDROID_STL=c++_shared")
                cppFlags += listOf("-std=c++20", "-ffp-contract=off", "-fno-fast-math")
            }
        }
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    sourceSets["main"].assets.srcDir(engineAssets)
    sourceSets["main"].assets.srcDir(shaderAssets)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

val stageEngineAssets by tasks.registering(Sync::class) {
    into(engineAssets)
    from(upstream.resolve("engine/resources")) { into("engine"); exclude("*.metallib") }
    from(upstream.resolve("engine/resources_product/profiles")) { into("engine/profiles") }
    from(upstream.resolve("engine/resources_product/fonts")) { into("engine/fonts"); include("*.ttf") }
    from(upstream.resolve("modern_UI/Spektrafilm/Spektrafilm/Resources/DI")) { into("engine/DI") }
    from(upstream.resolve("engine/resources_product/paper_grades.json")) { into("engine") }
    from(upstream.resolve("engine/resources_product/neutral_print_filters.json")) { into("engine"); rename { "product-neutral-print-filters.json" } }
    from(rootProject.file("LICENSES")) { into("licenses") }
    from(rootProject.file("NOTICE.md")) { into("licenses"); rename { "Android-NOTICE.md" } }
    from(rootProject.file("third_party/libraw/LICENSE.LGPL")) { into("licenses"); rename { "LibRaw-LGPL-2.1.txt" } }
    from(rootProject.file("third_party/libraw/COPYRIGHT")) { into("licenses"); rename { "LibRaw-COPYRIGHT.txt" } }
}
val compileEngineShaders by tasks.registering {
    dependsOn(stageEngineAssets)
    val source = upstream.resolve("engine/src/shaders/vulkan")
    inputs.files(fileTree(source) { include("*.comp", "*.glsl") })
    inputs.property("compiler", shaderCompiler)
    outputs.dir(shaderAssets.map { it.dir("engine/vulkan") })
    doLast {
        val output = shaderAssets.get().dir("engine/vulkan").asFile.apply { mkdirs() }
        fileTree(source) { include("*.comp") }.files.sortedBy { it.name }.forEach { shader ->
            providers.exec {
                commandLine(shaderCompiler.get(), "-V", "--target-env", "vulkan1.0",
                    "-I" + source.absolutePath, "-o", output.resolve(shader.nameWithoutExtension + ".spv").absolutePath,
                    shader.absolutePath)
            }.result.get().assertNormalExitValue()
        }
    }
}
tasks.configureEach {
    if ((name.startsWith("merge") && name.endsWith("Assets")) || name.contains("lint", ignoreCase = true)) {
        dependsOn(compileEngineShaders)
    }
}
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.annotation:annotation:1.9.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
