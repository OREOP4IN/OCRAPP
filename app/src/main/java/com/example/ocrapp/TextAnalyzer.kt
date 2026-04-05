package com.example.ocrapp

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextAnalyzer(
    private val throttleTimeoutMs: Long = 500L,
    private val isLiveScanningEnabled: () -> Boolean,
    private val onTextExtracted: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAnalyzedTimestamp = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // If live scanning is paused, immediately close the frame and do nothing
        if (!isLiveScanningEnabled()) {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalyzedTimestamp < throttleTimeoutMs) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            lastAnalyzedTimestamp = currentTime
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val cleanText = sanitizeForPrompt(visionText)
                    if (cleanText.isNotBlank()) {
                        onTextExtracted(cleanText)
                    }
                }
                .addOnFailureListener { e -> e.printStackTrace() }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }
}

fun sanitizeForPrompt(visionText: Text): String {
    val promptBuilder = StringBuilder()
    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            val rawLineText = line.text
            val alphaNumericCount = rawLineText.count { it.isLetterOrDigit() }
            val totalLength = rawLineText.length
            if (totalLength == 0) continue

            val validRatio = alphaNumericCount.toFloat() / totalLength
            if (validRatio > 0.5f) {
                val cleanLine = rawLineText.replace(Regex("[^a-zA-Z0-9.,!?'\"\\-:\\s]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (cleanLine.isNotBlank()) {
                    promptBuilder.append(cleanLine).append("\n")
                }
            }
        }
        promptBuilder.append("\n")
    }
    return promptBuilder.toString().trim()
}