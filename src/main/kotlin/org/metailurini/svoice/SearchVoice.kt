package org.metailurini.svoice

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.TextRange
import com.intellij.util.io.DataOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*

class SearchVoiceAction : AnAction() {
    private lateinit var tmpDir: Path
    private fun getTmpDir(): Path {
        if (!this::tmpDir.isInitialized) {
            this.tmpDir = Files.createTempDirectory("tmp")
        }
        return this.tmpDir
    }

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val tmpDir = this.getTmpDir()
        val listFileName = tmpDir.toFile().listFiles()!!.map { f -> f.absolutePath }

        if (editor != null) {
            val selectionStart: Int
            val selectionEnd: Int
            var selectedText = editor.selectionModel.selectedText
            if (selectedText == null) {
                val primaryCaret = editor.caretModel.primaryCaret
                selectionStart = editor.document.getLineStartOffset(primaryCaret.logicalPosition.line)
                selectionEnd = editor.document.getLineStartOffset(primaryCaret.logicalPosition.line)
                selectedText = editor.document.getText(TextRange(selectionStart, selectionEnd))
            }

            val soundName = Paths.get(tmpDir.toString(), hashString(selectedText))
            if (listFileName.indexOf(soundName.toAbsolutePath().toString()) == -1) {
                with(
                    URL("https://audio.api.speechify.dev/generateAudioFiles").openConnection() as HttpURLConnection
                ) {
                    requestMethod = "POST"
                    setRequestProperty("authority", "audio.api.speechify.dev")
                    setRequestProperty("accept", "*/*")
                    setRequestProperty("accept-base64", "true")
                    setRequestProperty("accept-language", "en-US,en;q=0.9,vi;q=0.8")
                    setRequestProperty("content-type", "application/json; charset=UTF-8")
                    setRequestProperty("dnt", "1")
                    setRequestProperty("x-speechify-client", "API")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true

                    DataOutputStream(outputStream).apply {
                        writeBytes(requestBody(selectedText).toString())
                        flush()
                        close()
                    }

                    BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
                        val response = StringBuffer()
                        var inputLine = bufferedReader.readLine()
                        while (inputLine != null) {
                            response.append(inputLine)
                            inputLine = bufferedReader.readLine()
                        }
                        val parseString = JsonParser.parseString(response.toString()).asJsonObject
                        val audioStream = parseString["audioStream"].asString
                        val binaryData = Base64.getDecoder().decode(audioStream)

                        Files.write(soundName, binaryData)
                    }
                }
            }

            println("run command $soundName")
            var processBuilder = ProcessBuilder(
                "/usr/bin/cvlc",
                "--play-and-exit",
                soundName.toAbsolutePath().toString(),
            )
            val process = processBuilder.start()
            val inputStreamReader = InputStreamReader(process.inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                println(line)
            }
        }
    }
}

fun requestBody(text: String): JsonObject {
    val jsonObject = JsonParser.parseString(
        """
                {
                    "audioFormat": "mp3",
                    "paragraphChunks": [],
                    "voiceParams": {
                        "name": "Matthew",
                        "engine": "neural",
                        "languageCode": "en-US"
                    }
                }
            """.trimIndent()
    ).asJsonObject
    val jsonElement = jsonObject["paragraphChunks"].asJsonArray
    jsonElement.add(text)
    return jsonObject
}

fun hashString(input: String, algorithm: String = "SHA-256"): String {
    val messageDigest = MessageDigest.getInstance(algorithm)
    val hashedBytes = messageDigest.digest(input.toByteArray())

    return hashedBytes.joinToString(separator = "") {
        String.format("%02x", it)
    }
}
