package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF242424) // Soft Dark Theme
                ) {
                    GhostStripperApp()
                }
            }
        }
    }
}

data class ExifData(
    val gps: String?,
    val time: String?,
    val device: String?,
    val cameraSettings: String?
)

sealed class AppState {
    object Idle : AppState()
    data class Analyzing(val uri: Uri) : AppState()
    data class Analyzed(val tempFile: File, val exifData: ExifData) : AppState()
    data class Removing(val progress: Int, val tempFile: File) : AppState()
    data class Success(val file: File) : AppState()
    data class Error(val message: String) : AppState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostStripperApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AppState>(AppState.Idle) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            state = AppState.Analyzing(uri)
            coroutineScope.launch {
                state = analyzeImage(context, uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metadata Remover", color = Color.White) },
                navigationIcon = {
                    if (state !is AppState.Idle) {
                        IconButton(onClick = { state = AppState.Idle }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        },
        containerColor = Color(0xFF242424) // Lightweight Dark Theme
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is AppState.Idle -> {
                    val stroke = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f)
                            .clip(RoundedCornerShape(16.dp))
                            .drawBehind {
                                drawRoundRect(color = Color.Gray, style = stroke, cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()))
                            }
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Using a simple text icon if actual icon fails to load or use coil painter
                            Text("⬆️", fontSize = 48.sp, modifier = Modifier.padding(bottom = 16.dp))
                            Text("एक फ़ोटो अपलोड करें", color = Color.White, fontSize = 20.sp)
                        }
                    }
                }
                is AppState.Analyzing -> {
                    CircularProgressIndicator(color = Color.White)
                }
                is AppState.Analyzed -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(currentState.tempFile),
                            contentDescription = "Analysis Preview",
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("GPS लोकेशन: ${currentState.exifData.gps ?: "-"}", color = Color.LightGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("समय: ${currentState.exifData.time ?: "-"}", color = Color.LightGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("उपकरण का नाम: ${currentState.exifData.device ?: "-"}", color = Color.LightGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("कैमरा सेटिंग्स: ${currentState.exifData.cameraSettings ?: "-"}", color = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        state = AppState.Removing(0, currentState.tempFile)
                                        // Simulate loading
                                        for (i in 1..10) {
                                            delay(150)
                                            state = AppState.Removing(i * 10, currentState.tempFile)
                                        }
                                        state = processExifRemoval(context, currentState.tempFile)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("डेटा मिटाएं")
                            }
                            Button(
                                onClick = {
                                    Toast.makeText(context, "यह फीचर जल्द आ रहा है", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("डेटा संपादित करें")
                            }
                        }
                    }
                }
                is AppState.Removing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            progress = { currentState.progress / 100f },
                            color = Color.White,
                            modifier = Modifier.size(80.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("डेटा मिटाया जा रहा है...", color = Color.White, fontSize = 18.sp)
                        Text("${currentState.progress}% पूर्ण", color = Color.LightGray, fontSize = 16.sp)
                    }
                }
                is AppState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(currentState.file),
                            contentDescription = "Safe Image",
                            modifier = Modifier
                                .size(300.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "डेटा सफलतापूर्वक मिटा दिया गया है!",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        saveToDownloads(context, currentState.file)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("फ़ोटो डाउनलोड करें")
                            }

                            Button(
                                onClick = {
                                    shareImage(context, currentState.file)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("शेयर करें")
                            }
                        }
                    }
                }
                is AppState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("त्रुटि: ${currentState.message}", color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { state = AppState.Idle }) {
                            Text("पुनः प्रयास करें")
                        }
                    }
                }
            }
        }
    }
}

suspend fun analyzeImage(context: Context, uri: Uri): AppState = withContext(Dispatchers.IO) {
    try {
        val tempFile = File(context.cacheDir, "temp_analyze.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext AppState.Error("Failed to open image")

        val exif = ExifInterface(tempFile.absolutePath)
        
        val gps = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.let {
            "$it ${exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)}"
        } ?: "-"
        
        val time = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "-"
        val device = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "-"
        
        val focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
        val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
        val cameraSettings = if (focalLength != null) "$focalLength mm, ISO $iso" else "-"

        val data = ExifData(gps, time, device, cameraSettings)
        AppState.Analyzed(tempFile, data)
    } catch (e: Exception) {
        e.printStackTrace()
        AppState.Error(e.message ?: "Unknown error")
    }
}

suspend fun processExifRemoval(context: Context, tempFile: File): AppState = withContext(Dispatchers.IO) {
    try {
        val exif = ExifInterface(tempFile.absolutePath)
        
        // Remove critical forensic metadata attributes
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, null)
        
        exif.setAttribute(ExifInterface.TAG_DATETIME, null)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
        
        exif.setAttribute(ExifInterface.TAG_MAKE, null)
        exif.setAttribute(ExifInterface.TAG_MODEL, null)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
        
        exif.setAttribute(ExifInterface.TAG_CAMERA_OWNER_NAME, null)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, null)
        
        exif.saveAttributes()

        val sharedDir = File(context.cacheDir, "shared_images")
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }
        
        val strippedFile = File(sharedDir, "Ghost_${System.currentTimeMillis()}.jpg")
        tempFile.copyTo(strippedFile, overwrite = true)
        
        AppState.Success(strippedFile)
    } catch (e: Exception) {
        e.printStackTrace()
        AppState.Error(e.message ?: "Unknown error occurred")
    }
}

suspend fun saveToDownloads(context: Context, file: File) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(uri, null, null)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()
        }
    }
}

fun shareImage(context: Context, file: File) {
    val authority = "${com.example.BuildConfig.APPLICATION_ID}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Cleaned Image"))
}
