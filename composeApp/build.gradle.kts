import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

kotlin {
    jvm("desktop")   // IMPORTANT FIX

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
            }
        }

        val desktopMain by getting {
            dependencies {

                // Apache POI for Excel file reading
                implementation("org.apache.poi:poi:5.2.5")
                implementation("org.apache.poi:poi-ooxml:5.2.5")

                // Required dependencies for Apache POI
                implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
                implementation("org.apache.commons:commons-compress:1.24.0")
                implementation("org.apache.commons:commons-collections4:4.4")

                implementation("technology.tabula:tabula:1.0.5")
                implementation("org.apache.pdfbox:pdfbox:2.0.29")

                implementation(compose.materialIconsExtended)
                implementation("io.ktor:ktor-client-core:2.3.5")
                implementation("io.ktor:ktor-client-cio:2.3.5")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")

                implementation("org.slf4j:slf4j-simple:2.0.9")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)

                implementation("com.google.auth:google-auth-library-oauth2-http:1.20.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Msi,
                TargetFormat.Dmg,
                TargetFormat.Deb
            )
            packageName = "org.example.project"
            packageVersion = "1.0.0"
        }
    }
}
