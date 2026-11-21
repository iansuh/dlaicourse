// Ktor game server: REST + WebSocket protocol (docs/protocol.md), SQLite storage, web pages from docs/play.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("app.numbaseball.server.MainKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.coroutines.test)
}

// The public web pages (privacy, terms, deletion) are written by the release lead in docs/play.
// They are bundled into the jar so the server and the Docker image always serve the reviewed text.
val playDocs = rootProject.layout.projectDirectory.dir("../docs/play")
tasks.processResources {
    from(playDocs) {
        include("PRIVACY_POLICY.md", "TERMS_OF_USE.md", "DELETE_ACCOUNT_PAGE.md")
        into("web")
    }
}

tasks.test {
    // Server tests use real sockets and timers; keep them in one JVM so ports and the DB stay simple.
    maxParallelForks = 1
}
