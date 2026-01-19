// hft-persistence: Persistence layer using Chronicle Queue for zero-GC, low-latency storage

val slf4jVersion: String by project
val junitVersion: String by project
val chronicleQueueVersion: String by project
val agronaVersion: String by project

dependencies {
    implementation(project(":hft-core"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.agrona:agrona:$agronaVersion")

    // Chronicle Queue for high-performance persistence
    implementation("net.openhft:chronicle-queue:$chronicleQueueVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

// JVM args needed for Chronicle on Java 21
tasks.withType<Test> {
    jvmArgs(
        "--add-exports", "java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-exports", "jdk.unsupported/sun.misc=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-opens", "jdk.compiler/com.sun.tools.javac=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED"
    )
}
