import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

val client = HttpClient(CIO) {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
}

fun main() = singleWindowApplication {
    MaterialTheme {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier.fillMaxSize()
        ) {
            AppScreen()
        }
    }
}

@Serializable
data class BrightnessData(
    val actual: Int,
    val max: Int
)

@Composable
fun AppScreen() {
    val coroutineScope = rememberCoroutineScope()
    val vm = remember { ViewModel(coroutineScope) }
    val brightnessData by vm.brightnessData
    Column {
        var sliderValue by remember { mutableStateOf(0f) }
        LaunchedEffect(brightnessData) {
            brightnessData?.actual?.let { sliderValue = it.toFloat() }
        }
        Text("Actual brightness: ${brightnessData?.actual ?: "Loading..."}")
        Text("Max brightness: ${brightnessData?.max ?: "Loading..."}")
        brightnessData?.let { brightnessData ->
            var job by remember { mutableStateOf<Job?>(null) }
            var lastVelocity by remember { mutableStateOf(0f) }
            Slider(
                value = sliderValue,
                onValueChange = {
                    job?.cancel()
                    job = coroutineScope.launch {
                        animate(
                            initialValue = sliderValue,
                            targetValue = it,
                            initialVelocity = lastVelocity
                        ) { value, velocity ->
                            lastVelocity = velocity
                            sliderValue = value
                            vm.setBrightness(value.roundToInt())
                        }
                    }
                },
                valueRange = 0f..brightnessData.max.toFloat()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var job by remember { mutableStateOf<Job?>(null) }
            var lastVelocity by remember { mutableStateOf(0f) }
            Button(onClick = {
                job?.cancel()
                job = coroutineScope.launch {
                    val random = brightnessData?.max?.let { Random.nextInt(0..it) } ?: 40000
                    animate(
                        initialValue = sliderValue,
                        targetValue = random.toFloat(),
                        initialVelocity = lastVelocity,
                        animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                    ) { value, velocity ->
                        lastVelocity = velocity
                        sliderValue = value
                        vm.setBrightness(value.roundToInt())
                    }
                }
            }) {
                Text("Random brightness")
            }
            Button(onClick = vm::powerOff) {
                Text("Power off")
            }
            Button(onClick = vm::subscribe) {
                Text("Refresh connection")
            }
        }
    }
    LaunchedEffect(vm) {
        vm.subscribe()
    }
}

class ViewModel(private val coroutineScope: CoroutineScope) {
    private val latestBrightness = MutableStateFlow<Int?>(null)
    private val _brightnessData = mutableStateOf<BrightnessData?>(null)
    val brightnessData: State<BrightnessData?> = _brightnessData

    init {
        subscribe()
    }

    fun subscribe() {
        coroutineScope.launch {
            runCatching {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = "localhost",
                    port = 8080,
                    path = "/"
                ) {
                    launch {
                        latestBrightness.collectLatest { value ->
                            if (value != null) {
                                if (_brightnessData.value?.actual != value)
                                    send(value.toString())
                            }
                        }
                    }
                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val data = Json.decodeFromString<BrightnessData>(frame.readText())
                        _brightnessData.value = data
                    }
                }
            }.onFailure {
                println("Failed to subscribe to brightness data!")
                println(it)
            }
        }
    }

    fun setBrightness(value: Int) {
        latestBrightness.update { value }
    }

    fun powerOff() {
        coroutineScope.launch {
            runCatching {
                client.get("http://localhost:8080/poweroff")
            }.onFailure {
                println("Failed to power off!")
                println(it)
            }
        }
    }
}
