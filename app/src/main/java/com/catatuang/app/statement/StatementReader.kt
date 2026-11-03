package com.catatuang.app.statement

import android.content.Context
import android.net.Uri
import com.catatuang.app.receipt.ReceiptOcr
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasswordRequiredException(val wrongPassword: Boolean) : Exception()

/** Gets the text out of a statement PDF. */
object StatementReader {

    /**
     * Reads the PDF's text layer (bank e-statements have one, so this is exact). If the PDF is a
     * scan with no text, falls back to OCR on each page. Throws [PasswordRequiredException] for
     * protected e-statements.
     */
    suspend fun readText(context: Context, uri: Uri, password: String? = null): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Can't open the file" }
                PDDocument.load(input, password.orEmpty()).use { doc ->
                    PDFTextStripper().apply { setSortByPosition(true) }.getText(doc)
                }
            }
        } catch (e: InvalidPasswordException) {
            throw PasswordRequiredException(wrongPassword = !password.isNullOrEmpty())
        }
        if (text.count { it.isLetterOrDigit() } > 50) text else ReceiptOcr.readPdfPages(context, uri, maxPages = 15)
    }
}
