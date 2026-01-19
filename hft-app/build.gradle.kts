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
        "-Xmx2g"
    )
}
