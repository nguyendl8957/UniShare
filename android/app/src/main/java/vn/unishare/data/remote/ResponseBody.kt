package vn.unishare.data.remote

import java.io.ByteArrayOutputStream
import java.io.InputStream

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
