package com.example.pocketsoccer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.drawscope.drawCircle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar permisos de sensor (normalmente no se necesitan,
        // pero en ciertos dispositivos con Android 13+ se pide permiso BODY_SENSORS).
        checkSensorPermission()

        setContent {
            MaterialTheme {
                PocketSoccerGame()
            }
        }
    }

    private fun checkSensorPermission() {
        // Si llegas a necesitar BODY_SENSORS en Android 13+, descomenta:
        /*
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                123
            )
        }
        */
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PocketSoccerGame() {
    // SensorManager y acelerómetro
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val accelerometer = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    // Estados para aceleración en X e Y
    val xAcceleration = remember { mutableStateOf(0f) }
    val yAcceleration = remember { mutableStateOf(0f) }

    // Listener del acelerómetro
    DisposableEffect(Unit) {
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                xAcceleration.value = event.values[0]
                yAcceleration.value = event.values[1]
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager.registerListener(
            sensorListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )
        onDispose {
            sensorManager.unregisterListener(sensorListener)
        }
    }

    // Estados para la pelota
    val ballX = remember { mutableStateOf(0f) }
    val ballY = remember { mutableStateOf(0f) }
    val velocityX = remember { mutableStateOf(0f) }
    val velocityY = remember { mutableStateOf(0f) }

    // Estado para el puntaje
    val score = remember { mutableStateOf(0) }

    // Variables de física simples
    val friction = 0.98f
    val speedFactor = 0.3f
    val maxSpeed = 25f
    val ballRadius = 20f

    // Tamaño real del contenedor donde está la cancha
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Contenedor que ocupa toda la pantalla
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    fieldSize = coords.size
                }
        ) {
            // Imagen de fondo a pantalla completa
            Image(
                painter = painterResource(id = R.drawable.cancha),
                contentDescription = "Fondo de cancha",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop // Llena la pantalla, recortando si es necesario
            )

            // Canvas para dibujar la pelota en el mismo contenedor
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Red,
                    radius = ballRadius,
                    center = Offset(ballX.value, ballY.value)
                )
            }
        }

        // Texto de goles en la parte superior
        Text(
            text = "Goles: ${score.value}",
            fontSize = 24.sp,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // Actualizar la posición de la pelota y colisiones
        SideEffect {
            val fieldWidthPx = fieldSize.width.toFloat()
            val fieldHeightPx = fieldSize.height.toFloat()

            // Límite izquierdo
            if (ballX.value - ballRadius < 0) {
                ballX.value = ballRadius
                velocityX.value = -velocityX.value
            }
            // Límite derecho
            if (ballX.value + ballRadius > fieldWidthPx) {
                ballX.value = fieldWidthPx - ballRadius
                velocityX.value = -velocityX.value
            }
            // Límite superior
            if (ballY.value - ballRadius < 0) {
                ballY.value = ballRadius
                velocityY.value = -velocityY.value
            }
            // Límite inferior
            if (ballY.value + ballRadius > fieldHeightPx) {
                ballY.value = fieldHeightPx - ballRadius
                velocityY.value = -velocityY.value
            }

            // Ejemplo de gol en la parte superior
            if (ballY.value - ballRadius <= 50f) {
                if (ballX.value in 200f..600f) {
                    score.value++
                    ballX.value = fieldWidthPx / 2
                    ballY.value = fieldHeightPx / 2
                    velocityX.value = 0f
                    velocityY.value = 0f
                }
            }
        }

        // Bucle de actualización de la pelota (~60 fps)
        LaunchedEffect(Unit) {
            while (true) {
                // Ajustar velocidad según la inclinación
                velocityX.value += -xAcceleration.value * speedFactor
                velocityY.value += yAcceleration.value * speedFactor

                // Limitar velocidad máxima
                if (abs(velocityX.value) > maxSpeed) {
                    velocityX.value = maxSpeed * (velocityX.value / abs(velocityX.value))
                }
                if (abs(velocityY.value) > maxSpeed) {
                    velocityY.value = maxSpeed * (velocityY.value / abs(velocityY.value))
                }

                // Aplicar fricción
                velocityX.value *= friction
                velocityY.value *= friction

                // Actualizar posición
                ballX.value += velocityX.value
                ballY.value += velocityY.value

                delay(16)
            }
        }
    }
}
