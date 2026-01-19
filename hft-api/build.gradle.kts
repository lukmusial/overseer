// hft-api: Spring Boot REST/WebSocket API

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":hft-core"))
    implementation(project(":hft-algo"))
    implementation(project(":hft-engine"))
    implementation(project(":hft-exchange-api"))
    implementation(project(":hft-risk"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}
