// hft-exchange-alpaca: Alpaca exchange adapter

val okhttpVersion: String by project
val jacksonVersion: String by project

dependencies {
    implementation(project(":hft-core"))
    implementation(project(":hft-exchange-api"))

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    // JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
}
