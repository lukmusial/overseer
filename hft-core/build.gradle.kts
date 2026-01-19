// hft-core: Domain models and interfaces (zero external dependencies)

val agronaVersion: String by project

dependencies {
    // Agrona for primitive collections and low-latency utilities
    implementation("org.agrona:agrona:$agronaVersion")
}
