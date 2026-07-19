// ============================================================
// PhoneHub - Gradle build using apktool for smali-based packaging
// ============================================================
// Usage: gradlew.bat assembleDebug
// Output: app/build/outputs/apk/debug/app-debug.apk
// ============================================================

plugins {
    id("com.android.application")
}

android {
    namespace = "com.phonehub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phonehub"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        dataBinding = false
        viewBinding = false
        buildConfig = false
    }
}

dependencies {
    // All libraries inlined into smali
}

// ============================================================
// Custom build: apktool assembles smali + resources -> APK
// ============================================================

val rootDirPath = rootProject.projectDir
val apktoolJar = file("$rootDirPath/apktool.jar")
val apkDecodedDir = file("$rootDirPath/apk_decoded")
val keystoreFile = file("$rootDirPath/phonehub.keystore")

// Task: build APK using apktool
val apktoolAssembleDebug by tasks.registering {
    description = "Build debug APK using apktool (assembles smali + resources)"
    group = "build"

    val outputApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val unsignedApk = layout.buildDirectory.file("intermediates/apktool/PhoneHub-unsigned.apk")

    inputs.dir(apkDecodedDir)
    inputs.file(apktoolJar)
    outputs.file(unsignedApk)

    doLast {
        val unsigned = unsignedApk.get().asFile
        unsigned.parentFile.mkdirs()

        logger.lifecycle("== apktool b ${apkDecodedDir.name} -> ${unsigned.name} ==")
        val pb = ProcessBuilder(
            System.getProperty("java.home") + "/bin/java",
            "-jar", apktoolJar.absolutePath,
            "b", apkDecodedDir.absolutePath,
            "-o", unsigned.absolutePath,
            "--use-aapt2"
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()
        if (output.isNotBlank()) logger.lifecycle(output)
        if (exitCode != 0) {
            throw GradleException("apktool build failed (exit $exitCode)")
        }
        logger.lifecycle("Built unsigned: ${unsigned.name} (${unsigned.length()} bytes)")
    }
}

// Task: sign the APK
val signDebugApk by tasks.registering {
    description = "Sign debug APK with jarsigner"
    group = "build"

    dependsOn(apktoolAssembleDebug)

    val unsignedApk = layout.buildDirectory.file("intermediates/apktool/PhoneHub-unsigned.apk")
    val signedApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")

    inputs.file(unsignedApk)
    outputs.file(signedApk)

    doLast {
        val unsigned = unsignedApk.get().asFile
        val signed = signedApk.get().asFile
        signed.parentFile.mkdirs()

        // Generate keystore if not exists
        if (!keystoreFile.exists()) {
            logger.lifecycle("Generating debug keystore...")
            val kspb = ProcessBuilder(
                System.getProperty("java.home") + "/bin/keytool",
                "-genkeypair", "-v",
                "-keystore", keystoreFile.absolutePath,
                "-alias", "phonehub",
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "36500",
                "-storepass", "phonehub123",
                "-keypass", "phonehub123",
                "-dname", "CN=PhoneHub, OU=Dev, O=PhoneHub, L=Beijing, S=Beijing, C=CN"
            )
            kspb.redirectErrorStream(true)
            val ksProc = kspb.start()
            ksProc.inputStream.bufferedReader().readText()
            ksProc.waitFor()
        }

        // Copy unsigned APK to output, then sign in-place
        unsigned.copyTo(signed, overwrite = true)

        logger.lifecycle("Signing APK with jarsigner...")
        val signPb = ProcessBuilder(
            System.getProperty("java.home") + "/bin/jarsigner",
            "-sigalg", "SHA256withRSA",
            "-digestalg", "SHA-256",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", "phonehub123",
            "-keypass", "phonehub123",
            signed.absolutePath,
            "phonehub"
        )
        signPb.redirectErrorStream(true)
        val signProc = signPb.start()
        signProc.inputStream.bufferedReader().readText()
        val signExit = signProc.waitFor()
        if (signExit != 0) {
            throw GradleException("jarsigner failed (exit $signExit)")
        }

        // Zipalign
        val buildToolsDir = File("$rootDirPath/android-sdk/build-tools")
        val btDir = buildToolsDir.listFiles()?.maxByOrNull { it.name } ?: File(buildToolsDir, "34.0.0")
        val zipalign = File(btDir, "zipalign.exe")
        if (zipalign.exists()) {
            logger.lifecycle("Zipaligning...")
            val aligned = File(signed.parentFile, "app-debug-aligned.apk")
            val zpb = ProcessBuilder(
                zipalign.absolutePath, "-f", "4",
                signed.absolutePath, aligned.absolutePath
            )
            zpb.redirectErrorStream(true)
            val zProc = zpb.start()
            zProc.inputStream.bufferedReader().readText()
            zProc.waitFor()
            aligned.copyTo(signed, overwrite = true)
            aligned.delete()
        }

        logger.lifecycle("== Output: ${signed.absolutePath} ==")
        logger.lifecycle("== Size: ${signed.length()} bytes ==")
    }
}

// Disable AGP tasks that would fail without Java sources (do this first, before AGP creates assembleDebug)
tasks.matching {
    it.name.startsWith("compile") ||
    it.name.startsWith("merge") ||
    it.name.startsWith("package") ||
    it.name.startsWith("process") ||
    it.name.startsWith("dataBinding") ||
    it.name.startsWith("generate") ||
    it.name.startsWith("check") ||
    it.name.startsWith("validate") ||
    it.name.startsWith("javaPreCompile") ||
    it.name.startsWith("kotlin")
}.configureEach {
    enabled = false
}

// Wire up assembleDebug/assemble to call our apktool-based build (after AGP evaluation)
afterEvaluate {
    // assembleDebug: register or wire up
    val existingAssembleDebug = tasks.findByName("assembleDebug")
    if (existingAssembleDebug != null) {
        // Clear existing dependencies to avoid circular deps, then depend on our signing task
        existingAssembleDebug.dependsOn.clear()
        existingAssembleDebug.dependsOn(signDebugApk)
    } else {
        tasks.register("assembleDebug") {
            group = "build"
            description = "Builds the debug APK using apktool"
            dependsOn(signDebugApk)
        }
    }

    // assemble: register or wire up
    val existingAssemble = tasks.findByName("assemble")
    if (existingAssemble != null) {
        existingAssemble.dependsOn(signDebugApk)
    } else {
        tasks.register("assemble") {
            group = "build"
            description = "Assembles all outputs"
            dependsOn(signDebugApk)
        }
    }

    // Also expose build lifecycle
    val existingBuild = tasks.findByName("build")
    if (existingBuild != null) {
        existingBuild.dependsOn(signDebugApk)
    }
}
