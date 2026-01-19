// hft-risk: Risk management module

val agronaVersion: String by project

dependencies {
    implementation(project(":hft-core"))
    implementation("org.agrona:agrona:$agronaVersion")
}
