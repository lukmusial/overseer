plugins {
    java
    id("org.springframework.boot") version "3.2.2" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

val javaVersion: String by project
val disruptorVersion: String by project
val agronaVersion: String by project
val chronicleWireVersion: String by project
val okhttpVersion: String by project
val jacksonVersion: String by project
val junitVersion: String by project
val cucumberVersion: String by project
val jmhVersion: String by project
val mockitoVersion: String by project
val slf4jVersion: String by project
val logbackVersion: String by project

allprojects {
    group = property("group") as String
    version = property("version") as String
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "-Xlint:all",
            "-Xlint:-processing",
            "--enable-preview"
        ))
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            excludeTags("integration")
        }
        jvmArgs(
            "--enable-preview",
            "-XX:+UseZGC",
            "-XX:+ZGenerational"
        )
    }

    // Dedicated task for running integration tests (e.g., Alpaca sandbox)
    tasks.register<Test>("integrationTest") {
        useJUnitPlatform {
            includeTags("integration")
        }
        jvmArgs(
            "--enable-preview",
            "-XX:+UseZGC",
            "-XX:+ZGenerational"
        )
    }

    dependencies {
        // Logging
        implementation("org.slf4j:slf4j-api:$slf4jVersion")
        runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

        // Testing
        testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
        testImplementation("org.mockito:mockito-core:$mockitoVersion")
        testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}

// JVM args for running the application with low-latency settings
tasks.register("printJvmArgs") {
    doLast {
        println("""
            Recommended JVM args for low-latency:
            -XX:+UseZGC
            -XX:+ZGenerational
            -XX:+AlwaysPreTouch
            -XX:+UseNUMA
            -XX:+DisableExplicitGC
            -Xms4g
            -Xmx4g
            -Djava.lang.Integer.IntegerCache.high=65536
        """.trimIndent())
    }
}
