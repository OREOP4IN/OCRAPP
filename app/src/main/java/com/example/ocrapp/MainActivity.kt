package com.example.ocrapp // Replace with your actual package name
import androidx.compose.material3.OutlinedButton
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

// --- Gemini-Inspired Dark Mode Palette ---
val GeminiDarkBg = Color(0xFF131314)
val GeminiSurface = Color(0xFF1E1F22)
val GeminiBlue = Color(0xFF8AB4F8)
val GeminiPurple = Color(0xFFD7B7FD)
val GeminiGradient = Brush.linearGradient(listOf(GeminiBlue, GeminiPurple))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = GeminiDarkBg, surface = GeminiSurface)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CameraPermissionWrapper()
                }
            }
        }
    }
}

@Composable
fun CameraPermissionWrapper() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        CameraTextRecognitionScreen()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required to extract text.", color = Color.White)
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraTextRecognitionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current // Used for screen height

    var extractedText by remember { mutableStateOf("Aim at text or capture an image...") }
    var isSquareRatio by remember { mutableStateOf(false) }
    var isLiveScanning by remember { mutableStateOf(true) }
    var showTopControls by remember { mutableStateOf(false) }
    var isGeminiLoading by remember { mutableStateOf(false) }
    var isFullScreenText by remember { mutableStateOf(false) } // NEW: Full screen state
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }

    val cropImageLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful && result.uriContent != null) {
            extractedText = "Extracting text..."
            processStaticImage(context, result.uriContent!!) { cleanText ->
                extractedText = cleanText.ifBlank { "No text found." }
            }
        } else {
            extractedText = "Crop cancelled."
            isLiveScanning = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            isLiveScanning = false
            cropImageLauncher.launch(getGeminiCropOptions(uri))
        }
    }

    // GEMINI LOGIC HELPER
    fun runGeminiPrompt(action: String) {
        if (extractedText.isBlank() || extractedText.startsWith("Aim at")) return
        isGeminiLoading = true
        coroutineScope.launch {
            try {
                // IMPORTANT: Read from BuildConfig securely
                val generativeModel = GenerativeModel("gemini-2.5-flash", BuildConfig.GEMINI_API_KEY)

                val prompt = if (action == "FIX") {
                    """
                    You are an expert data cleaner. Take the following raw OCR text and format it perfectly. 
                    Fix any obvious spelling mistakes caused by bad scanning, organize it into clean paragraphs or lists if applicable, and remove random garbage symbols. 
                    Do NOT answer questions or add outside information. Just return the cleaned text.
                    
                    Raw Text:
                    $extractedText
                    """.trimIndent()
                } else {
                    """
                    Find the problem within the input and solve it. Provide a clear, concise, and accurate response based strictly on the text provided. If problem could be solved by code, solve with python3 unless stated otherwise.
                    
                    Text:
                    $extractedText
                    """.trimIndent()
                }

                val response = generativeModel.generateContent(prompt)
                extractedText = response.text ?: "Error generating content."

                // NEW: Enlarge the box to show the full answer
                isFullScreenText = true
            } catch (e: Exception) {
                extractedText = "Gemini Error: ${e.localizedMessage}"
            } finally {
                isGeminiLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // LAYER 1: THE CAMERA
        Box(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                .then(if (isSquareRatio) Modifier.aspectRatio(1f) else Modifier.fillMaxSize())
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val cameraExecutor = Executors.newSingleThreadExecutor()
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                        imageCaptureUseCase = capture

                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor, TextAnalyzer(
                                    throttleTimeoutMs = 500L,
                                    isLiveScanningEnabled = { isLiveScanning }
                                ) { text -> extractedText = text })
                            }

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, imageAnalyzer)
                            val cameraControl = camera.cameraControl
                            previewView.setOnTouchListener { view, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                                    cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                                    view.performClick()
                                }
                                true
                            }
                        } catch (exc: Exception) { exc.printStackTrace() }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        // LAYER 2: TOP CONTROLS
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .padding(top = 48.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = { showTopControls = !showTopControls },
                modifier = Modifier.size(40.dp).background(GeminiSurface.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = if (showTopControls) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Settings", tint = Color.White
                )
            }
            AnimatedVisibility(
                visible = showTopControls,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it }
            ) {
                Button(
                    onClick = {
                        isSquareRatio = !isSquareRatio
                        showTopControls = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(if (isSquareRatio) "Switch to Full Screen" else "Switch to 1:1 Square", color = Color.White)
                }
            }
        }

        // LAYER 3: BOTTOM SHEET & MAIN CONTROLS
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, GeminiDarkBg.copy(alpha = 0.8f), GeminiDarkBg)))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // NEW: Animate the height based on the full screen state
            val animatedHeight by animateDpAsState(
                targetValue = if (isFullScreenText) (configuration.screenHeightDp * 0.75f).dp else 180.dp,
                animationSpec = tween(durationMillis = 400),
                label = "textBoxHeight"
            )

            // Extracted Text Box (NOW EDITABLE AND EXPANDABLE)
            Box(
                modifier = Modifier.fillMaxWidth().height(animatedHeight).clip(RoundedCornerShape(16.dp))
                    // Lower opacity so the camera feed shows through like a watermark
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(BorderStroke(1.dp, if (isLiveScanning) GeminiGradient else SolidColor(Color.DarkGray)), RoundedCornerShape(16.dp))
            ) {
                TextField(
                    value = extractedText,
                    onValueChange = { extractedText = it },
                    enabled = !isLiveScanning, // Can only edit when paused
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.9f), lineHeight = 24.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = GeminiBlue
                    ),
                    modifier = Modifier.fillMaxSize().padding(end = 40.dp, top = if (isFullScreenText) 40.dp else 0.dp)
                )

                // Minimize Button (Only visible when expanded)
                if (isFullScreenText) {
                    IconButton(
                        onClick = { isFullScreenText = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Minimize", tint = Color.White)
                    }
                }

                // Copy Button
                IconButton(
                    onClick = {
                        if (extractedText.isNotBlank() && !extractedText.contains("Aim at text")) {
                            clipboardManager.setText(AnnotatedString(extractedText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy text", tint = GeminiBlue)
                }
            }

            // AI Action Row (Only appears when paused)
            AnimatedVisibility(
                visible = !isLiveScanning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { runGeminiPrompt("FIX") },
                        enabled = !isGeminiLoading,
                        border = BorderStroke(1.dp, if (isGeminiLoading) Color.Gray else GeminiBlue),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = GeminiBlue,
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isGeminiLoading) "PROCESSING..." else "FIX OCR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { runGeminiPrompt("SOLVE") },
                        enabled = !isGeminiLoading,
                        border = BorderStroke(1.dp, if (isGeminiLoading) Color.Gray else GeminiBlue),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = GeminiBlue,
                            disabledContentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isGeminiLoading) "PROCESSING..." else "SOLVE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons Row (Gallery, Capture, Pause)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GALLERY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(16.dp)
                )

                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White)
                        .clickable {
                            val capture = imageCaptureUseCase ?: return@clickable
                            isLiveScanning = false
                            extractedText = "Processing..."
                            val tempFile = File.createTempFile("ocr_capture_", ".jpg", context.cacheDir)
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(tempFile).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        cropImageLauncher.launch(getGeminiCropOptions(Uri.fromFile(tempFile)))
                                    }
                                    override fun onError(exc: ImageCaptureException) { isLiveScanning = true }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(64.dp).border(2.dp, Color.Black, CircleShape))
                }

                Text(
                    text = if (isLiveScanning) "PAUSE" else "RESUME",
                    color = if (isLiveScanning) GeminiBlue else Color.LightGray,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        isLiveScanning = !isLiveScanning
                        if (isLiveScanning) isFullScreenText = false // NEW: Auto-shrink if resuming camera
                    }.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Helper for Cropper Theme
private fun getGeminiCropOptions(uri: Uri): CropImageContractOptions {
    return CropImageContractOptions(
        uri = uri,
        cropImageOptions = CropImageOptions(
            guidelinesColor = android.graphics.Color.parseColor("#8AB4F8"),
            borderLineColor = android.graphics.Color.parseColor("#8AB4F8"),
            borderCornerColor = android.graphics.Color.parseColor("#8AB4F8"),
            backgroundColor = android.graphics.Color.parseColor("#CC131314"),
            activityTitle = "", activityMenuIconColor = android.graphics.Color.WHITE,
            guidelines = com.canhub.cropper.CropImageView.Guidelines.ON_TOUCH,
            borderCornerThickness = 8f, borderLineThickness = 3f,
            showProgressBar = false, imageSourceIncludeGallery = false, imageSourceIncludeCamera = false
        )
    )
}

// Helper to process Uris
private fun processStaticImage(context: Context, uri: Uri, onResult: (String) -> Unit) {
    try {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromFilePath(context, uri))
            .addOnSuccessListener { visionText -> onResult(sanitizeForPrompt(visionText)) }
            .addOnFailureListener { onResult("Error processing image.") }
    } catch (e: Exception) {
        onResult("Error loading image.")
    }
}