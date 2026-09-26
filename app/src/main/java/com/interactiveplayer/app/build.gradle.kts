plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.interactiveplayer.app"
    compileSdk = 34
    buildToolsVersion = "33.0.2"

    signingConfigs {
        // O Actions gera uma keystore de debug nova a cada execução, e
        // assinatura diferente faz o Android recusar a atualização —
        // era isso que obrigava a desinstalar o app a cada versão. Com
        // uma keystore fixa versionada junto, todo APK sai com a mesma
        // assinatura e instala por cima. Ela é só de debug: a senha é
        // pública de propósito e não serve pra publicar na Play Store.
        getByName("debug") {
            storeFile = rootProject.file("keystore/maximus-debug.keystore")
            storePassword = "android"
            keyAlias = "maximusdebug"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.interactiveplayer.app"
        minSdk = 23
        targetSdk = 34
        // Sobe sozinho a cada build do Actions, senão o Android trata
        // como "mesma versão" e pode recusar a instalação por cima.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // A chave do TMDb vem do ambiente (secret TMDB_API_KEY no GitHub
        // Actions), nunca do código-fonte. Sem ela, o app compila e roda
        // normalmente — só não enriquece os títulos com gênero, igual ao
        // comportamento do original quando EXPO_PUBLIC_TMDB_API_KEY está
        // vazia.
        buildConfigField(
            "String",
            "TMDB_API_KEY",
            "\"${System.getenv("TMDB_API_KEY") ?: ""}\"",
        )
    }

    buildTypes {
        create("nativeTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".native"
            versionNameSuffix = "-native-test"
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // BUG corrigido: ScoreActivity/SportsClient usam java.time (LocalDate,
        // Instant, DateTimeFormatter, ZoneOffset) pra calcular os dias do
        // "Placar". Essas classes só existem nativamente a partir do Android
        // 8.0 (API 26) -- com minSdk=23 e sem desugaring, numa TV box rodando
        // Android 7.x ou mais antigo (bem comum nesse tipo de aparelho), a
        // primeira chamada (LocalDate.now(...) em SportsClient.fetchDays)
        // derruba o app na hora com NoClassDefFoundError, sem try/catch que
        // segure -- exatamente o "clico em Placar e fecha o aplicativo".
        // Habilitar o desugaring faz o Android incluir uma implementação
        // dessas classes no próprio APK, então elas passam a funcionar em
        // qualquer versão do minSdk sem precisar reescrever esse código.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
        )
    }
}

dependencies {
    // Ver isCoreLibraryDesugaringEnabled acima -- fornece a implementação de
    // java.time pra rodar em qualquer minSdk, corrigindo o crash do Placar.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
