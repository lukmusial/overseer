// hft-persistence: Persistence layer (Chronicle-based zero-GC storage to be added later)

dependencies {
    implementation(project(":hft-core"))

    // TODO: Add Chronicle Wire when stable version compatible with Java 21 is available
    // implementation("net.openhft:chronicle-wire:$chronicleWireVersion")
}
