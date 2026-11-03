package com.catatuang.app.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads text from a receipt (screenshot or PDF) on-device with ML Kit (no network, no cost). */
object ReceiptOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(context: Context, uri: Uri, mimeType: String? = null): String {
        val type = mimeType ?: context.contentResolver.getType(uri)
        val image = if (type == "application/pdf") {
            InputImage.fromBitmap(renderFirstPdfPage(context, uri), 0)
        } else {
            InputImage.fromFilePath(context, uri)
        }
        return recognize(image)
    }

    private suspend fun recognize(image: InputImage): String {
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val boxes = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val r = line.boundingBox ?: return@mapNotNull null
                OcrBox(line.text, r.top, r.bottom, r.left)
            }
        }
        return if (boxes.isNotEmpty()) ReceiptParser.joinRows(boxes) else result.text
    }

    /** Some banks share the receipt as a PDF; render page 1 at ~2x so small text stays readable. */
    private suspend fun renderFirstPdfPage(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Can't open the PDF")
        fd.use {
            PdfRenderer(it).use { pdf ->
                pdf.openPage(0).use { page ->
                    val scale = 2
                    val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }
}
