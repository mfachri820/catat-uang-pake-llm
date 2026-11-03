package com.catatuang.app.receipt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads text from a receipt screenshot on-device with ML Kit (no network, no cost). */
object ReceiptOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
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
}
