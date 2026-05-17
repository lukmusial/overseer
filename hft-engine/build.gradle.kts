// hft-engine: Order matching and event processing with LMAX Disruptor

plugins {
    `java-library`
}

val disruptorVersion: String by project
val agronaVersion: String by project
val affinityVersion: String by project
val junitVersion: String by project
val mockitoVersion: String by project

dependencies {
    implementation(project(":hft-core"))
    implementation(project(":hft-exchange-api"))
    implementation(project(":hft-risk"))
    implementation(project(":hft-persistence"))

    // LMAX Disruptor for lock-free inter-thread messaging
    // Exposed as 'api' because WaitStrategy appears in engine's public constructors
    api("com.lmax:disruptor:$disruptorVersion")
    implementation("org.agrona:agrona:$agronaVersion")

    // OpenHFT thread affinity for pinning the Disruptor consumer to a CPU core.
    // No-ops on macOS (OS doesn't expose kernel affinity); fully functional on Linux.
    implementation("net.openhft:affinity:$affinityVersion")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
}
