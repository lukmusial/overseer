rootProject.name = "hft-client"

include(
    "hft-core",
    "hft-algo",
    "hft-exchange-api",
    "hft-exchange-alpaca",
    "hft-exchange-binance",
    "hft-risk",
    "hft-engine",
    "hft-persistence",
    "hft-api",
    "hft-app",
    "hft-bdd"
)

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
