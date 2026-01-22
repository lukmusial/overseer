// hft-app: Main application assembly

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":hft-core"))
    implementation(project(":hft-algo"))
    implementation(project(":hft-engine"))
    implementation(project(":hft-exchange-api"))
    implementation(project(":hft-exchange-alpaca"))
    implementation(project(":hft-exchange-binance"))
    implementation(project(":hft-risk"))
    implementation(project(":hft-persistence"))
    implementation(project(":hft-api"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Frontend build configuration
val uiDir = file("${rootProject.projectDir}/hft-ui")
val staticDir = file("${projectDir}/src/main/resources/static")

tasks.register<Exec>("npmInstall") {
    description = "Install frontend dependencies"
    workingDir = uiDir
    commandLine("npm", "install")
    inputs.file("${uiDir}/package.json")
    inputs.file("${uiDir}/package-lock.json")
    outputs.dir("${uiDir}/node_modules")
}

tasks.register<Exec>("buildFrontend") {
    description = "Build the frontend application"
    dependsOn("npmInstall")
    workingDir = uiDir
    commandLine("npm", "run", "build")
    inputs.dir("${uiDir}/src")
    inputs.file("${uiDir}/package.json")
    inputs.file("${uiDir}/vite.config.ts")
    inputs.file("${uiDir}/tsconfig.json")
    outputs.dir("${uiDir}/dist")
}

tasks.register<Copy>("copyFrontend") {
    description = "Copy frontend build to static resources"
    dependsOn("buildFrontend")
    from("${uiDir}/dist")
    into(staticDir)
}

// Make processResources depend on copyFrontend
tasks.processResources {
    dependsOn("copyFrontend")
}

// Convenience task to build everything (backend + frontend)
tasks.register("buildAll") {
    description = "Build backend and frontend together"
    group = "build"
    dependsOn("build")
}

// Skip frontend build with -PskipFrontend flag for faster backend-only builds
if (project.hasProperty("skipFrontend")) {
    tasks.named("npmInstall") { enabled = false }
    tasks.named("buildFrontend") { enabled = false }
    tasks.named("copyFrontend") { enabled = false }
}

tasks.bootJar {
    mainClass.set("com.hft.app.HftApplication")
}

// Low-latency JVM configuration for bootRun
tasks.bootRun {
    jvmArgs = listOf(
        "--enable-preview",
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
        "-XX:+AlwaysPreTouch",
        "-Xms2g",
        "-Xmx2g",
        // Chronicle Queue requirements for Java 11+
        "--add-exports", "java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-exports", "jdk.unsupported/sun.misc=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED"
    )
}
