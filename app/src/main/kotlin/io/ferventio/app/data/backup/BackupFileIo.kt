package io.ferventio.app.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

object BackupFileIo {
    const val MAX_BACKUP_FILE_BYTES = 1_048_576

    fun readUtf8Limited(
        input: InputStream,
        maxBytes: Int = MAX_BACKUP_FILE_BYTES,
    ): String {
        require(maxBytes > 0) { "Лимит файла должен быть положительным" }
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Файл больше ${maxBytes / 1_048_576} МБ" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    fun writeUtf8(output: OutputStream, content: String) {
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }
}
