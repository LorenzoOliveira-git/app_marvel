plugins {
    alias(libs.plugins.android.application)
}

// A configuração Firebase é fornecida localmente ou pelo ambiente de compilação.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.app_marvel"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.app_marvel"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "FIREBASE_EMULATORS", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "FIREBASE_EMULATORS", providers.gradleProperty("firebaseEmulators").map { (it == "true").toString() }.orElse("false").get())
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.core.splashscreen)
    implementation(libs.mlkit.translate)
    implementation(libs.recyclerview)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

// Desenvolvimento local: transfere a credencial por stdin/ADB, nunca por BuildConfig/assets.
abstract class LocalComicVineTask : DefaultTask() {
    @get:Internal abstract val adbExecutable: RegularFileProperty
    @get:Internal abstract val environmentFile: RegularFileProperty
    @get:Internal abstract val apkFile: RegularFileProperty
    @get:Internal abstract val deviceSerial: Property<String>
    @get:Internal abstract val installAndLaunch: Property<Boolean>

    private fun adb(serial: String?, arguments: List<String>, input: ByteArray? = null): String {
        val command = listOf(adbExecutable.get().asFile.absolutePath) +
                (if (serial == null) emptyList() else listOf("-s", serial)) + arguments
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.outputStream.use { if (input != null) it.write(input) }
        // A saída do ADB não contém a chave; nunca incluir a entrada nas mensagens.
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) throw GradleException("Falha no ADB. Confira aparelho autorizado e instalação debug.")
        return output
    }

    @TaskAction fun configureDevice() {
        val localFile = environmentFile.get().asFile
        val localKey = if (localFile.isFile) localFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
            .lineSequence().map { it.trim().removePrefix("export ").trim() }
            .filter { it.substringBefore('=').trim() == "COMICVINE_API_KEY" && '=' in it }
            .map { it.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'") }
            .lastOrNull() else null
        val key = System.getenv("COMICVINE_API_KEY")?.takeIf { it.isNotBlank() } ?: localKey
        if (key == null || !Regex("[A-Za-z0-9_-]{16,256}").matches(key))
            throw GradleException("Configure COMICVINE_API_KEY no .env da raiz do projeto. Não envie a chave ao GitHub.")
        val devices = adb(null, listOf("devices")).lineSequence().map { it.trim().split(Regex("\\s+")) }
            .filter { it.size >= 2 && it[1] == "device" }.map { it[0] }.toList()
        val selected = deviceSerial.orNull?.takeIf { it.isNotBlank() }
            ?: devices.singleOrNull()
            ?: throw GradleException("Inicie um único emulador/aparelho autorizado ou use -PcomicvineDevice=SERIAL.")
        if (selected !in devices) throw GradleException("O aparelho selecionado não está conectado/autorizado.")
        if (installAndLaunch.get()) adb(selected, listOf("install", "-r", apkFile.get().asFile.absolutePath))
        val bytes = key.toByteArray(Charsets.UTF_8)
        try {
            adb(selected, listOf("shell", "run-as", "com.example.app_marvel", "sh", "-c",
                "'umask 077; mkdir -p no_backup; cat > no_backup/comicvine-api-key.tmp && mv no_backup/comicvine-api-key.tmp no_backup/comicvine-api-key'"), bytes)
        } finally { bytes.fill(0) }
        if (installAndLaunch.get()) {
            adb(selected, listOf("shell", "am", "force-stop", "com.example.app_marvel"))
            adb(selected, listOf("shell", "am", "start", "-n", "com.example.app_marvel/.MainActivity"))
        }
        logger.lifecycle("ComicVine configurada no aparelho; credencial fora do APK. Pode usar Run/Debug normalmente.")
    }
}

fun LocalComicVineTask.localConfiguration() {
    group = "application"
    adbExecutable.set(androidComponents.sdkComponents.adb)
    environmentFile.set(rootProject.layout.projectDirectory.file(".env"))
    apkFile.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    deviceSerial.set(providers.gradleProperty("comicvineDevice").orElse(providers.environmentVariable("ANDROID_SERIAL")).orElse(""))
    notCompatibleWithConfigurationCache("Lê credencial local somente durante o provisionamento privado por ADB.")
}

tasks.register<LocalComicVineTask>("configureComicVineDebug") {
    description = "Configura a ComicVine em uma instalação debug existente, a partir do .env local."
    localConfiguration(); installAndLaunch.set(false)
}
tasks.register<LocalComicVineTask>("runLocalDebug") {
    description = "Compila, instala, configura a ComicVine pelo .env e abre o app no aparelho escolhido."
    dependsOn("assembleDebug")
    localConfiguration(); installAndLaunch.set(true)
}
