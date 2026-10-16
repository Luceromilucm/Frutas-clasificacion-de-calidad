package com.example.app_frutas

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.*
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

data class Prediccion(val clase: String, val prob: Float, val top3: List<Pair<String, Float>>)

class Clasificador(context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labels: List<String>
    private val size = 224
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)   // ImageNet (EfficientNet)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    init {
        val modelo = context.assets.open("effnet_b0_tl.onnx").readBytes()
        session = env.createSession(modelo, OrtSession.SessionOptions())
        labels = context.assets.open("labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }

    fun clasificar(foto: Bitmap): Prediccion {
        val input = aTensor(letterbox(foto))
        val forma = longArrayOf(1, 3, size.toLong(), size.toLong())
        val logits = OnnxTensor.createTensor(env, input, forma).use { t ->
            session.run(mapOf("input" to t)).use { out ->
                @Suppress("UNCHECKED_CAST")
                (out[0].value as Array<FloatArray>)[0]
            }
        }
        val p = softmax(logits)
        val top3 = p.indices.sortedByDescending { p[it] }.take(3).map { labels[it] to p[it] }
        return Prediccion(top3[0].first, top3[0].second, top3)
    }

    // Igual que el notebook: escala sin deformar y rellena con la mediana del color del borde
    private fun letterbox(src: Bitmap): Bitmap {
        val color = colorBorde(src)
        val s = size.toFloat() / max(src.width, src.height)
        val w = max(1, (src.width * s).roundToInt())
        val h = max(1, (src.height * s).roundToInt())
        val chica = Bitmap.createScaledBitmap(src, w, h, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(color)
        c.drawBitmap(chica, ((size - w) / 2).toFloat(), ((size - h) / 2).toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun colorBorde(b: Bitmap): Int {
        val w = b.width; val h = b.height
        val arriba = IntArray(w); val abajo = IntArray(w); val izq = IntArray(h); val der = IntArray(h)
        b.getPixels(arriba, 0, w, 0, 0, w, 1)
        b.getPixels(abajo, 0, w, 0, h - 1, w, 1)
        b.getPixels(izq, 0, 1, 0, 0, 1, h)
        b.getPixels(der, 0, 1, w - 1, 0, 1, h)
        val px = arriba + abajo + izq + der
        fun mediana(canal: (Int) -> Int) = px.map(canal).sorted()[px.size / 2]
        return Color.rgb(mediana { Color.red(it) }, mediana { Color.green(it) }, mediana { Color.blue(it) })
    }

    // ToTensor + Normalize, formato 1x3x224x224 (canal por canal)
    private fun aTensor(b: Bitmap): FloatBuffer {
        val area = size * size
        val px = IntArray(area)
        b.getPixels(px, 0, size, 0, 0, size, size)
        val buf = FloatBuffer.allocate(3 * area)
        for (i in 0 until area) {
            val p = px[i]
            buf.put(i, (Color.red(p) / 255f - mean[0]) / std[0])
            buf.put(area + i, (Color.green(p) / 255f - mean[1]) / std[1])
            buf.put(2 * area + i, (Color.blue(p) / 255f - mean[2]) / std[2])
        }
        return buf
    }

    private fun softmax(x: FloatArray): FloatArray {
        val m = x.max()
        val e = x.map { exp(it - m) }
        val s = e.sum()
        return e.map { it / s }.toFloatArray()
    }

    fun cerrar() = session.close()
}
