package com.example.pocketsoccer

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.drawscope.drawCircle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
        // En la mayoría de los casos no es necesario,
        // pero si en tu caso requieres BODY_SENSORS, descomenta:
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
                // event.values[0] => aceleración en eje X (inclinación izq-der)
                // event.values[1] => aceleración en eje Y (inclinación arriba-abajo)
                // event.values[2] => eje Z (no lo usamos aquí)
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

    // Variables de “física” simples
    val friction = 0.98f      // fricción para simular rozamiento
    val speedFactor = 0.3f    // factor para ajustar respuesta a la inclinación
    val maxSpeed = 25f        // velocidad máxima de la pelota
    val ballRadius = 20f      // radio de la pelota en px (aprox)

    // Área de gol (definir de manera simplificada)
    // Por ejemplo, en la parte superior central
    // Ajusta las coordenadas una vez sepas el tamaño real
    // En este ejemplo, la portería es un rectángulo en la parte superior
    // Podrías hacer otra en la parte inferior o como gustes.
    val goalTopLeftX = 200f
    val goalTopRightX = 600f
    val goalTopY = 0f
    val goalHeight = 50f

    // Para actualizar la posición en un bucle
    LaunchedEffect(Unit) {
        while (true) {
            // Ajustar velocidad según aceleración
            velocityX.value += -xAcceleration.value * speedFactor
            velocityY.value += yAcceleration.value * speedFactor

            // Limitar la velocidad máxima
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

            // Esperar un frame (~16 ms para ~60 fps)
            delay(16)
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Dimensiones del contenedor (la "cancha")
        val fieldWidthPx = constraints.maxWidth.toFloat()
        val fieldHeightPx = constraints.maxHeight.toFloat()

        // Checar colisiones con los bordes y rebotar
        // Se hace en un SideEffect o en la misma LaunchedEffect
        // Para simplificar, lo hacemos en un SideEffect que se ejecuta cada recomposición
        SideEffect {
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

            // Detectar gol: si la pelota está en la región de la portería
            // Aquí un ejemplo para portería superior
            if (ballY.value - ballRadius <= goalTopY + goalHeight) {
                if (ballX.value in goalTopLeftX..goalTopRightX) {
                    // ¡Gol!
                    score.value++
                    // Reiniciar posición de la pelota al centro
                    ballX.value = fieldWidthPx / 2
                    ballY.value = fieldHeightPx / 2
                    velocityX.value = 0f
                    velocityY.value = 0f
                }
            }
        }

        // Fondo con la imagen de la cancha
        Image(
            painter = painterResource(id = R.drawable.cancha),
            contentDescription = "Fondo de cancha",
            modifier = Modifier.fillMaxSize()
        )

        // Dibujo de la pelota en la posición actual
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            drawCircle(
                color = Color.Red,
                radius = ballRadius,
                center = androidx.compose.ui.geometry.Offset(ballX.value, ballY.value)
            )
        }

        // Mostrar marcador
        Text(
            text = "Goles: ${score.value}",
            fontSize = 24.sp,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
    }
}
