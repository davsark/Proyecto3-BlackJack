plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

dependencies {
    // Proyecto común (protocolo compartido)
    implementation(project(":composeApp"))

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Serialización JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Testing
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("org.example.project.server.GameServerKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
