plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM module: no Android, no UI, no ads, no billing.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
