# BrightnessServerClient

`app` is a Compose Multiplatform desktop client communicating to server using Ktor client.

`server` is a Kotlin/Native Ktor server watching brightness changes (on Intel LCDs) and relaying them to the client through a WebSocket endpoint.
Client can also send new brightness values for the server to set (requires server be run as `root`).
Server also exposes a `/poweroff` endpoint to exit the server process.

> This was a quick experiment I made sometime by the end of 2024, archived for historic reasons.
> The initial git commit was made in March 3rd, 2026, but committed with the last file modification date.
> 
> The purpose was experimenting with the viability of easier interaction with native APIs from Compose/JVM apps
> through a native server binary with Kotlin/Native. This could be improved with kotlinx.rpc and shared types.

