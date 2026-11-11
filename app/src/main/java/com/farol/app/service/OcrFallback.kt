package com.farol.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Plano B para quando o app de corrida não expõe o texto da oferta para a acessibilidade
 * (views desenhadas em Canvas, Compose sem semântica etc.).
 *
 * Tira um print pela própria API de acessibilidade (Android 11+, sem pedir permissão de
 * gravação de tela) e roda OCR on-device com ML Kit. Nada sai do aparelho.
 * Não funciona em janelas com FLAG_SECURE (o print sai preto).
 */
class OcrFallback(private val svc: AccessibilityService) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var busy = false
    private var lastRun = 0L
    private val minIntervalMs = 1500L // o Android limita prints a ~1 por segundo

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Altura da barra de status: o OCR ignora o que estiver nela (relógio, "400 m" do GPS, km/h). */
    private val statusBarPx: Int by lazy {
        val id = svc.resources.getIdentifier("status_bar_height", "dimen", "android")
        (if (id > 0) svc.resources.getDimensionPixelSize(id) else 0) + 8
    }
    private val NOISE = Regex("""km/h|toque para retornar|aplicativos ativos""", RegexOption.IGNORE_CASE)

    /** @param exclude área do nosso próprio cartão, para não lermos o que nós mesmos escrevemos. */
    fun run(exclude: Rect?, onText: (String) -> Unit) {
        if (!supported || busy) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRun < minIntervalMs) return
        lastRun = now
        busy = true
        capture(exclude, onText)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capture(exclude: Rect?, onText: (String) -> Unit) {
        svc.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            svc.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val hb = result.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp == null) { busy = false; return }

                    recognizer.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener { vt ->
                            val text = vt.textBlocks
                                .flatMap { it.lines }
                                .filter { line ->
                                    val box = line.boundingBox
                                    (exclude == null || box == null || !Rect.intersects(exclude, box)) &&
                                        (box == null || box.top > statusBarPx) &&
                                        !NOISE.containsMatchIn(line.text)
                                }
                                .joinToString(" | ") { it.text }
                            onText(text)
                        }
                        .addOnCompleteListener {
                            busy = false
                            bmp.recycle()
                        }
                }

                override fun onFailure(errorCode: Int) { busy = false }
            }
        )
    }

    fun close() = recognizer.close()
}
