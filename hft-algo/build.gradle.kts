// hft-algo: Trading algorithms (VWAP, TWAP, Momentum, Mean Reversion)

val junitVersion: String by project
val mockitoVersion: String by project

dependencies {
    implementation(project(":hft-core"))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
}
