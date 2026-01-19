// hft-engine: Order matching and event processing with LMAX Disruptor

val disruptorVersion: String by project
val agronaVersion: String by project
val junitVersion: String by project
val mockitoVersion: String by project

dependencies {
    implementation(project(":hft-core"))
    implementation(project(":hft-exchange-api"))
    implementation(project(":hft-risk"))
    implementation(project(":hft-persistence"))

    // LMAX Disruptor for lock-free inter-thread messaging
    implementation("com.lmax:disruptor:$disruptorVersion")
    implementation("org.agrona:agrona:$agronaVersion")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")
}
