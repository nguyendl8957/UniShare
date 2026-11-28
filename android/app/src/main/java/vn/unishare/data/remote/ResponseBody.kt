package vn.unishare.data.remote

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val PDF_HEADER_PROBE_BYTES = 1024
private val pdfHeader = Regex("^(?:\\u00EF\\u00BB\\u00BF)?[\\u0000\\t\\n\\u000C\\r ]*%PDF-(?:1\\.[0-7]|2\\.0)[\\r\\n]")

internal fun hasPdfHeader(bytes: ByteArray): Boolean =
    pdfHeader.containsMatchIn(String(bytes, 0, minOf(bytes.size, PDF_HEADER_PROBE_BYTES), Charsets.ISO_8859_1))

internal fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val buffer = ByteArray(minOf(8192, maxBytes))
    val output = ByteArrayOutputStream()
    var remaining = maxBytes
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        output.write(buffer, 0, count)
        remaining -= count
    }
    return output.toByteArray()
}
