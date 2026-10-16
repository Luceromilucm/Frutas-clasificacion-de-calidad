package com.example.app_frutas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var clasificador: Clasificador
    private lateinit var vista: PreviewView

    // UI Elements - Header & Scanner
    private lateinit var btnFlash: ImageButton
    private lateinit var viewScannerFrame: View

    // UI Elements - Bottom Card
    private lateinit var tvFruitName: TextView
    private lateinit var badgeStatus: LinearLayout
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusText: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvLatency: TextView

    // UI Elements - Top 3 Breakdown
    private lateinit var tvCandidateName1: TextView
    private lateinit var progressCandidate1: ProgressBar
    private lateinit var tvCandidateProb1: TextView

    private lateinit var tvCandidateName2: TextView
    private lateinit var progressCandidate2: ProgressBar
    private lateinit var tvCandidateProb2: TextView

    private lateinit var tvCandidateName3: TextView
    private lateinit var progressCandidate3: ProgressBar
    private lateinit var tvCandidateProb3: TextView

    // Camera & Background Thread
    private var camera: Camera? = null
    private var isTorchEnabled = false
    private val hilo = Executors.newSingleThreadExecutor()

    private val pedirCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) {
                iniciarCamara()
            } else {
                tvFruitName.text = "Sin acceso a cámara"
                tvConfidence.text = "Concede permisos de cámara en los ajustes para escanear."
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        clasificador = Clasificador(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            pedirCamara.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initViews() {
        vista = findViewById(R.id.vista)
        btnFlash = findViewById(R.id.btnFlash)
        viewScannerFrame = findViewById(R.id.viewScannerFrame)

        tvFruitName = findViewById(R.id.tvFruitName)
        badgeStatus = findViewById(R.id.badgeStatus)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        tvStatusText = findViewById(R.id.tvStatusText)
        tvConfidence = findViewById(R.id.tvConfidence)
        tvLatency = findViewById(R.id.tvLatency)

        tvCandidateName1 = findViewById(R.id.tvCandidateName1)
        progressCandidate1 = findViewById(R.id.progressCandidate1)
        tvCandidateProb1 = findViewById(R.id.tvCandidateProb1)

        tvCandidateName2 = findViewById(R.id.tvCandidateName2)
        progressCandidate2 = findViewById(R.id.progressCandidate2)
        tvCandidateProb2 = findViewById(R.id.tvCandidateProb2)

        tvCandidateName3 = findViewById(R.id.tvCandidateName3)
        progressCandidate3 = findViewById(R.id.progressCandidate3)
        tvCandidateProb3 = findViewById(R.id.tvCandidateProb3)

        btnFlash.setOnClickListener {
            toggleTorch()
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            isTorchEnabled = !isTorchEnabled
            cam.cameraControl.enableTorch(isTorchEnabled)
            btnFlash.setImageResource(
                if (isTorchEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        } else {
            Toast.makeText(this, "El dispositivo no cuenta con flash", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val proveedor = futuro.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(vista.surfaceProvider)
            }
            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analisis.setAnalyzer(hilo) { img -> analizar(img) }

            proveedor.unbindAll()
            camera = proveedor.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analisis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analizar(img: ImageProxy) {
        val foto = img.toBitmap().rotar(img.imageInfo.rotationDegrees)
        img.close()

        val t0 = System.currentTimeMillis()
        val p = clasificador.clasificar(foto)
        val ms = System.currentTimeMillis() - t0

        runOnUiThread {
            actualizarUI(p, ms)
        }
    }

    private fun actualizarUI(p: Prediccion, ms: Long) {
        tvLatency.text = "⚡ ${ms} ms"

        val esConfiable = p.prob >= 0.60f
        val (fruta, estado, esFresca) = parsearClase(p.clase)

        if (!esConfiable) {
            tvFruitName.text = "Escaneando..."
            tvConfidence.text = "Centra una fruta en el recuadro para clasificarla"

            badgeStatus.setBackgroundResource(R.drawable.bg_pill_neutral)
            ivStatusIcon.setImageResource(R.drawable.ic_sparkles)
            tvStatusText.text = "EN BUSCA"
            tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            tvFruitName.text = fruta
            val pct = "%.1f".format(p.prob * 100)
            tvConfidence.text = "Certeza: $pct% · Estado detectado: $estado"

            if (esFresca) {
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_fresh)
                ivStatusIcon.setImageResource(R.drawable.ic_leaf)
                tvStatusText.text = "FRESCA"
                tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.fresh_green))
            } else {
                badgeStatus.setBackgroundResource(R.drawable.bg_pill_rotten)
                ivStatusIcon.setImageResource(R.drawable.ic_alert)
                tvStatusText.text = "PODRIDA"
                tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.rotten_red))
            }
        }

        // Top 3 breakdown rendering
        if (p.top3.isNotEmpty()) {
            val (nombre1, prob1) = p.top3[0]
            tvCandidateName1.text = formatearEtiqueta(nombre1)
            progressCandidate1.progress = (prob1 * 100).toInt()
            tvCandidateProb1.text = "${(prob1 * 100).toInt()}%"
        }

        if (p.top3.size > 1) {
            val (nombre2, prob2) = p.top3[1]
            tvCandidateName2.text = formatearEtiqueta(nombre2)
            progressCandidate2.progress = (prob2 * 100).toInt()
            tvCandidateProb2.text = "${(prob2 * 100).toInt()}%"
        }

        if (p.top3.size > 2) {
            val (nombre3, prob3) = p.top3[2]
            tvCandidateName3.text = formatearEtiqueta(nombre3)
            progressCandidate3.progress = (prob3 * 100).toInt()
            tvCandidateProb3.text = "${(prob3 * 100).toInt()}%"
        }
    }

    private data class InfoFruta(val fruta: String, val estado: String, val esFresca: Boolean)

    private fun parsearClase(clase: String): InfoFruta {
        val esFresca = clase.startsWith("Fresh")
        val estado = if (esFresca) "Fresca" else "Podrida"
        val fruta = when (clase.removePrefix("Fresh").removePrefix("Rotten")) {
            "Apple" -> "Manzana"
            "Banana" -> "Plátano"
            "Orange" -> "Naranja"
            "Strawberry" -> "Fresa"
            else -> clase
        }
        return InfoFruta(fruta, estado, esFresca)
    }

    private fun formatearEtiqueta(clase: String): String {
        val (fruta, estado) = parsearClase(clase)
        return "$fruta $estado"
    }

    private fun Bitmap.rotar(grados: Int): Bitmap {
        if (grados == 0) return this
        val matrix = Matrix().apply { postRotate(grados.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        hilo.shutdown()
        clasificador.cerrar()
    }
}