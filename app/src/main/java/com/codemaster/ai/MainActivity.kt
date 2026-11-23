package com.codemaster.ai

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadButton: Button
    private lateinit var sendButton: Button
    private lateinit var input: EditText
    private var modelReady = false
    private val history = StringBuilder()
    private val modelFile by lazy { File(filesDir, "Qwen3-4B-Q4_K_M.gguf") }

    private external fun nativeLoadModel(path: String): String
    private external fun nativeGenerate(prompt: String): String
    private external fun nativeUnload()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        System.loadLibrary("codemaster-native")
        buildScreen()
        if (modelFile.exists() && modelFile.length() > 2_000_000_000L) {
            setStatus("Your AI model is saved. Loading it…")
            loadModel()
        } else {
            setStatus("Download the 2.5 GB model once using Wi-Fi. After that, chat offline.")
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 14)
            setBackgroundColor(Color.rgb(13, 17, 27))
        }
        val title = TextView(this).apply {
            text = "⌘  CodeMaster AI"
            textSize = 25f
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "PRIVATE · OFFLINE · NO API KEY"
            textSize = 11f
            setTextColor(Color.rgb(100, 213, 220))
        }
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 8)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            max = 100
        }
        downloadButton = Button(this).apply {
            text = "Download offline AI model (2.5 GB)"
            setOnClickListener { downloadModel() }
        }
        val scroll = ScrollView(this)
        transcript = TextView(this).apply {
            text = "Welcome! I'm CodeMaster AI. After installing its model, I can help write, explain and debug code without internet.\n\n"
            textSize = 15f
            setTextColor(Color.rgb(235, 240, 250))
            setPadding(8, 14, 8, 14)
        }
        scroll.addView(transcript)
        input = EditText(this).apply {
            hint = "Ask me to write or fix code…"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.rgb(28, 39, 58))
            minLines = 2
            maxLines = 5
            setPadding(12, 8, 12, 8)
        }
        sendButton = Button(this).apply {
            text = "Send"
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(progress, LinearLayout.LayoutParams(-1, 12))
        root.addView(downloadButton)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(input)
        root.addView(sendButton)
        setContentView(root)
    }

    private fun setStatus(value: String) = main.post { status.text = value }

    private fun downloadModel() {
        downloadButton.isEnabled = false
        progress.visibility = View.VISIBLE
        setStatus("Connecting to Hugging Face… Keep this app open and use Wi-Fi.")
        worker.execute {
            val temp = File(filesDir, "model-download.tmp")
            try {
                val url = URL("https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "CodeMasterAI-Android")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) throw Exception("Download server returned HTTP " + connection.responseCode)
                val total = connection.contentLengthLong
                var received = 0L
                BufferedInputStream(connection.inputStream).use { stream ->
                    FileOutputStream(temp).use { out ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            received += count
                            if (total > 0) {
                                val percent = (received * 100 / total).toInt()
                                main.post { progress.progress = percent; status.text = "Downloading AI model… " + percent + "% (" + (received / 1000000000L) + " GB)" }
                            } else {
                                setStatus("Downloading AI model… " + (received / 1000000000L) + " GB")
                            }
                        }
                    }
                }
                if (temp.length() < 2000000000L) throw Exception("Downloaded file is too small. Please retry on Wi-Fi.")
                if (modelFile.exists()) modelFile.delete()
                if (!temp.renameTo(modelFile)) throw Exception("Could not save the model. Check phone storage.")
                main.post { progress.progress = 100 }
                loadModel()
            } catch (e: Exception) {
                temp.delete()
                main.post {
                    status.text = "Download failed: " + e.message
                    downloadButton.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun loadModel() {
        downloadButton.isEnabled = false
        setStatus("Loading the offline model into memory. This can take a minute…")
        worker.execute {
            try {
                val result = nativeLoadModel(modelFile.absolutePath)
                if (result != "READY") throw Exception(result)
                modelReady = true
                main.post {
                    status.text = "Offline AI ready · Qwen3 4B · No account, API key or internet needed"
                    downloadButton.visibility = View.GONE
                    progress.visibility = View.GONE
                    sendButton.isEnabled = true
                    transcript.append("AI model loaded successfully. Ask me anything about coding!\n\n")
                }
            } catch (e: Exception) {
                main.post {
                    status.text = "Could not load model: " + e.message
                    downloadButton.text = "Retry loading model"
                    downloadButton.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun sendMessage() {
        val question = input.text.toString().trim()
        if (question.isEmpty() || !modelReady) return
        input.setText("")
        transcript.append("\nYOU\n" + question + "\n\nCODEMASTER AI\nThinking…\n")
        sendButton.isEnabled = false
        val system = "You are CodeMaster AI, an expert coding assistant and patient teacher. Help with any programming language, software, websites, apps, games, APIs, databases, and Minecraft modding. Give accurate, complete, secure code with filenames and setup instructions when useful. Explain errors and fixes step by step for a beginner. Never claim code was run or tested unless it was. If a request is unclear, ask one focused question. Use clear headings and code blocks."
        history.append("<|im_start|>user\n").append(question).append("<|im_end|>\n<|im_start|>assistant\n")
        val prompt = "<|im_start|>system\n" + system + " /no_think<|im_end|>\n" + history.toString()
        worker.execute {
            val answer = try { nativeGenerate(prompt) } catch (e: Exception) { "Error: " + e.message }
            history.append(answer).append("<|im_end|>\n")
            main.post {
                val current = transcript.text.toString()
                val idx = current.lastIndexOf("Thinking…")
                transcript.text = if (idx >= 0) current.substring(0, idx) + answer + "\n\n" else current + answer + "\n\n"
                sendButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        if (modelReady) worker.execute { nativeUnload() }
        worker.shutdown()
        super.onDestroy()
    }
}
