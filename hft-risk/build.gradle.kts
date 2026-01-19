// hft-risk: Risk management module

val agronaVersion: String by project
val slf4jVersion: String by project
val junitVersion: String by project

dependencies {
    implementation(project(":hft-core"))
    implementation("org.agrona:agrona:$agronaVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}
