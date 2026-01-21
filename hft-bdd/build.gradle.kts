// hft-bdd: Cucumber BDD tests and JMH benchmarks

plugins {
    id("io.spring.dependency-management")
}

val cucumberVersion: String by project
val jmhVersion: String by project
val okhttpVersion: String by project
val springBootVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
}

dependencies {
    // JMH Benchmarks
    testImplementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")

    testImplementation(project(":hft-core"))
    testImplementation(project(":hft-algo"))
    testImplementation(project(":hft-engine"))
    testImplementation(project(":hft-risk"))
    testImplementation(project(":hft-persistence"))
    testImplementation(project(":hft-exchange-api"))
    testImplementation(project(":hft-exchange-alpaca"))
    testImplementation(project(":hft-exchange-binance"))
    testImplementation(project(":hft-api"))

    // Cucumber BDD
    testImplementation("io.cucumber:cucumber-java:$cucumberVersion")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:$cucumberVersion")

    // JUnit Platform Suite
    testImplementation("org.junit.platform:junit-platform-suite:1.10.2")

    // HTTP client for API testing
    testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    // JSON processing
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}

tasks.test {
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
