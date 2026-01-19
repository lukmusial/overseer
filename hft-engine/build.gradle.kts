// hft-engine: Order matching and event processing with LMAX Disruptor

val disruptorVersion: String by project
val agronaVersion: String by project

dependencies {
    implementation(project(":hft-core"))
    implementation(project(":hft-exchange-api"))
    implementation(project(":hft-risk"))

    // LMAX Disruptor for lock-free inter-thread messaging
    implementation("com.lmax:disruptor:$disruptorVersion")
    implementation("org.agrona:agrona:$agronaVersion")
}
