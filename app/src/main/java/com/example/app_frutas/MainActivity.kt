package com.example.app_frutas

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
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
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.card.MaterialCardView
import java.io.InputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private enum class AppMode { LIVE, STATIC }

    private lateinit var clasificador: Clasificador
    private lateinit var vista: PreviewView

    // Static Preview Elements (Framed card, no full-screen stretching)
    private lateinit var viewDimOverlay: View
    private lateinit var cardStaticPreview: MaterialCardView
    private lateinit var ivStaticPreview: ImageView
    private lateinit var tvStaticBadge: TextView

    // UI Elements - Header & Scanner
    private lateinit var btnFlash: ImageButton
    private lateinit var tvModeBadge: TextView
    private lateinit var viewScannerFrame: View
    private lateinit var tvScannerTip: TextView

    // Action Controls
    private lateinit var btnGallery: LinearLayout
    private lateinit var btnCapturePhoto: FrameLayout
    private lateinit var btnLiveMode: LinearLayout
    private lateinit var ivLiveIcon: ImageView
    private lateinit var tvLiveButtonText: TextView

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

    // Camera & Background Execution
    private var camera: Camera? = null
    private var isTorchEnabled = false
    private var currentMode = AppMode.LIVE
    private val hilo = Executors.newSingleThreadExecutor()

    // Permission launcher
    private val pedirCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) {
                iniciarCamara()
            } else {
                tvFruitName.text = "Sin acceso a cámara"
                tvConfidence.text = "Concede permisos o usa 'Galería' para clasificar fotos."
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_LONG).show()
            }
        }

    // Gallery Picker launcher
    private val seleccionarFoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { procesarFotoDeGaleria(it) }
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
        viewDimOverlay = findViewById(R.id.viewDimOverlay)
        cardStaticPreview = findViewById(R.id.cardStaticPreview)
        ivStaticPreview = findViewById(R.id.ivStaticPreview)
        tvStaticBadge = findViewById(R.id.tvStaticBadge)

        btnFlash = findViewById(R.id.btnFlash)
        tvModeBadge = findViewById(R.id.tvModeBadge)
        viewScannerFrame = findViewById(R.id.viewScannerFrame)
        tvScannerTip = findViewById(R.id.tvScannerTip)

        btnGallery = findViewById(R.id.btnGallery)
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto)
        btnLiveMode = findViewById(R.id.btnLiveMode)
        ivLiveIcon = findViewById(R.id.ivLiveIcon)
        tvLiveButtonText = findViewById(R.id.tvLiveButtonText)

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

        btnFlash.setOnClickListener { toggleTorch() }
        btnGallery.setOnClickListener { seleccionarFoto.launch("image/*") }
        btnCapturePhoto.setOnClickListener { capturarFotoActual() }
        btnLiveMode.setOnClickListener { activarModoEnVivo() }
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

            analisis.setAnalyzer(hilo) { img ->
                if (currentMode == AppMode.LIVE) {
                    analizarFrameEnVivo(img)
                } else {
                    img.close()
                }
            }

            proveedor.unbindAll()
            camera = proveedor.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analisis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    // Crops the central area matching the visual scanner box on screen
    private fun recortarRecuadro(bitmap: Bitmap): Bitmap {
        val minDim = minOf(bitmap.width, bitmap.height)
        val cropSize = (minDim * 0.82f).toInt()
        val cropX = (bitmap.width - cropSize) / 2
        val cropY = (bitmap.height - cropSize) / 2
        return Bitmap.createBitmap(bitmap, cropX, cropY, cropSize, cropSize)
    }

    private fun analizarFrameEnVivo(img: ImageProxy) {
        val fotoRotada = img.toBitmap().rotar(img.imageInfo.rotationDegrees)
        img.close()

        // Recorte exacto al recuadro para que la fruta ocupe todo el espacio de inferencia
        val fotoRecortada = recortarRecuadro(fotoRotada)

        val t0 = System.currentTimeMillis()
        val p = clasificador.clasificar(fotoRecortada)
        val ms = System.currentTimeMillis() - t0

        runOnUiThread {
            if (currentMode == AppMode.LIVE) {
                actualizarUI(p, ms, esModoFoto = false)
            }
        }
    }

    // Capture the current frame as a static snapshot, cropped to the reticle
    private fun capturarFotoActual() {
        val bitmap = vista.bitmap
        if (bitmap != null) {
            val fotoRecortada = recortarRecuadro(bitmap)
            cambiarAModoEstatico(fotoRecortada, "Foto Capturada")
        } else {
            Toast.makeText(this, "Cámara no lista aún", Toast.LENGTH_SHORT).show()
        }
    }

    // Process image picked from Gallery
    private fun procesarFotoDeGaleria(uri: Uri) {
        try {
            val bitmapOriginal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            // Adjust orientation if EXIF metadata contains rotation
            val bitmapRotado = corregirRotacionExif(uri, bitmapOriginal)
            cambiarAModoEstatico(bitmapRotado, "Foto de Galería")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al cargar la imagen seleccionada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun corregirRotacionExif(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                val orientacion = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                inputStream.close()
                val rotacion = when (orientacion) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                bitmap.rotar(rotacion)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun cambiarAModoEstatico(bitmap: Bitmap, origen: String) {
        currentMode = AppMode.STATIC

        // Elegant framed presentation: dim live preview and show centered card
        ivStaticPreview.setImageBitmap(bitmap)
        viewDimOverlay.visibility = View.VISIBLE
        cardStaticPreview.visibility = View.VISIBLE
        tvStaticBadge.text = origen.uppercase()

        viewScannerFrame.visibility = View.GONE
        tvScannerTip.visibility = View.GONE

        tvModeBadge.text = "FOTO FIJA"
        tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))

        tvLiveButtonText.text = "Volver a Vivo"
        tvLiveButtonText.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
        ivLiveIcon.setImageResource(R.drawable.ic_refresh)

        // Classify on background thread
        hilo.execute {
            val t0 = System.currentTimeMillis()
            val p = clasificador.clasificar(bitmap)
            val ms = System.currentTimeMillis() - t0

            runOnUiThread {
                if (currentMode == AppMode.STATIC) {
                    actualizarUI(p, ms, esModoFoto = true, origenFoto = origen)
                }
            }
        }
    }

    private fun activarModoEnVivo() {
        if (currentMode == AppMode.LIVE) {
            Toast.makeText(this, "Ya estás en modo de cámara en vivo", Toast.LENGTH_SHORT).show()
            return
        }

        currentMode = AppMode.LIVE

        // Hide static card and dim overlay, restore reticle
        viewDimOverlay.visibility = View.GONE
        cardStaticPreview.visibility = View.GONE

        viewScannerFrame.visibility = View.VISIBLE
        tvScannerTip.visibility = View.VISIBLE

        tvModeBadge.text = "EN VIVO"
        tvModeBadge.setTextColor(ContextCompat.getColor(this, R.color.fresh_green))

        tvLiveButtonText.text = "En Vivo"
        tvLiveButtonText.setTextColor(ContextCompat.getColor(this, R.color.fresh_green))
        ivLiveIcon.setImageResource(R.drawable.ic_live)
    }

    private fun actualizarUI(
        p: Prediccion,
        ms: Long,
        esModoFoto: Boolean,
        origenFoto: String = ""
    ) {
        tvLatency.text = "⚡ ${ms} ms"

        val esConfiable = p.prob >= 0.50f
        val (fruta, estado, esFresca) = parsearClase(p.clase)

        if (!esConfiable) {
            tvFruitName.text = if (esModoFoto) "Fruta no identificada" else "Escaneando..."
            tvConfidence.text = if (esModoFoto) {
                "$origenFoto · Intenta con una imagen más nítida de la fruta"
            } else {
                "Centra una fruta en el recuadro para clasificarla"
            }

            badgeStatus.setBackgroundResource(R.drawable.bg_pill_neutral)
            ivStatusIcon.setImageResource(R.drawable.ic_sparkles)
            tvStatusText.text = "EN DUDA"
            tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            tvFruitName.text = fruta
            val pct = "%.1f".format(p.prob * 100)
            val prefijo = if (esModoFoto) "[$origenFoto] " else ""
            tvConfidence.text = "${prefijo}Certeza: $pct% · Estado: $estado"

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