import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsLogger
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

val fileSystem = FileSystem.SYSTEM

@Serializable
data class BrightnessData(
    val actual: Int,
    val max: Int
)

fun main() {
    embeddedServer(factory = CIO, configure = {
        connector { port = 8080 }
        reuseAddress = true
    }) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        routing {
            webSocket("/") {
                var lastBrightness = actualBrightness
                suspend fun sendBrightness() {
                    lastBrightness = actualBrightness
                    sendSerialized(BrightnessData(lastBrightness, maxBrightness))
                }
                println("Connected!")
                sendBrightness()
                val scope = CoroutineScope(coroutineContext)
                val watcher = KfsDirectoryWatcher(scope, logger = object : KfsLogger {
                    override fun debug(message: String) = Unit
                    override fun error(message: String) = println("[KfsWatcher] error: $message")
                })
                watcher.add("/sys/class/backlight/intel_backlight/")
                launch {
                    println("Watching...")
                    watcher.onEventFlow.collect {
                        println("Directory change detected!")
                        if (actualBrightness != lastBrightness) sendBrightness()
                    }
                }
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val value = frame.readText().toIntOrNull()
                    if (value != null) setBrightness(value)
                }
            }
            get("/poweroff") {
                call.respond(HttpStatusCode.OK)
                println("Shutting down...")
                this@embeddedServer.engine.stop()
            }
        }
    }.start(wait = true)
}

fun setBrightness(value: Int) {
    fileSystem.write("/sys/class/backlight/intel_backlight/brightness".toPath()) {
        writeUtf8(value.coerceIn(0..maxBrightness).toString())
    }
}

val actualBrightness
    get() = fileSystem.read("/sys/class/backlight/intel_backlight/actual_brightness".toPath()) {
        readUtf8()
    }.trim().toInt()

val maxBrightness
    get() = fileSystem.read("/sys/class/backlight/intel_backlight/max_brightness".toPath()) {
        readUtf8()
    }.trim().toInt()
