package app.kite.agent.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Local document text extraction. No server, no root: PDF / DOCX / PPTX / XLSX /
 * plain text all become text that the model can read.
 */
object DocumentParser {

    private const val MAX_CHARS = 120_000
    private var pdfReady = false

    fun parse(context: Context, uri: Uri, name: String, mime: String): String {
        val lower = name.lowercase()
        val text = when {
            lower.endsWith(".pdf") || mime == "application/pdf" -> pdf(context, uri)
            lower.endsWith(".docx") -> zipText(context, uri, setOf("word/document.xml"), "t")
            lower.endsWith(".pptx") -> zipText(context, uri, null, "t")
            lower.endsWith(".xlsx") -> zipText(context, uri, null, "t")
            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv") ||
                lower.endsWith(".json") || lower.endsWith(".log") || lower.endsWith(".xml") ||
                lower.endsWith(".html") || lower.endsWith(".htm") || mime.startsWith("text/") ->
                plain(context, uri)
            else -> throw IllegalArgumentException("暂不支持这个文件类型：$name")
        }.trim()
        if (text.isBlank()) throw IllegalArgumentException("文件里没有可读文字：$name")
        return text.take(MAX_CHARS)
    }

    private fun pdf(context: Context, uri: Uri): String {
        if (!pdfReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfReady = true
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("打不开 PDF")
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                return PDFTextStripper().getText(document)
            }
        }
    }

    private fun plain(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("打不开文件")
        input.use { stream ->
            return stream.bufferedReader().use { it.readText() }.take(MAX_CHARS)
        }
    }

    /**
     * Extract all text runs from OOXML zip. For docx only the main document;
     * for pptx/xlsx all slide/sheet/sharedStrings parts.
     */
    private fun zipText(context: Context, uri: Uri, only: Set<String>?, tag: String): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("打不开文件")
        val out = StringBuilder()
        input.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name
                    val wanted = only?.contains(entryName)
                        ?: (entryName.endsWith(".xml") &&
                            (entryName.startsWith("ppt/slides/") ||
                                entryName.startsWith("xl/worksheets/") ||
                                entryName == "xl/sharedStrings.xml"))
                    if (wanted && !entry.isDirectory) {
                        out.append(xmlText(zip, tag)).append('\n')
                    }
                    zip.closeEntry()
                }
            }
        }
        return out.toString()
    }

    private fun xmlText(input: InputStream, tag: String): String {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val out = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG &&
                (parser.name == tag || parser.name.endsWith(":$tag"))
            ) {
                val value = runCatching { parser.nextText() }.getOrNull()
                if (!value.isNullOrBlank()) out.append(value).append(' ')
            }
            event = parser.next()
        }
        return out.toString()
    }
}
