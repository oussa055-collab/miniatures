package com.miniatures

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
private val VID = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v")

class MainActivity : ComponentActivity() {
    private val granted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true).build()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    if (granted.value) Browser(loader) else Ask()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted.value = Environment.isExternalStorageManager()
    }

    @Composable
    fun Ask() {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("L'application a besoin d'accéder à vos fichiers pour afficher les miniatures.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            }) { Text("Autoriser l'accès") }
        }
    }

    @Composable
    fun Browser(loader: ImageLoader) {
        val root = Environment.getExternalStorageDirectory()
        var dir by remember { mutableStateOf(root) }
        val items = remember(dir) {
            (dir.listFiles()?.toList() ?: emptyList())
                .filter { !it.name.startsWith(".") }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
        BackHandler(enabled = dir != root) { dir = dir.parentFile ?: root }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                dir.path.removePrefix(root.path).ifEmpty { "/" },
                Modifier.padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            LazyVerticalGrid(
                GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.path }) { f ->
                    Tile(f, loader) { if (f.isDirectory) dir = f else open(f) }
                }
            }
        }
    }

    @Composable
    fun Tile(f: File, loader: ImageLoader, onClick: () -> Unit) {
        val ext = f.extension.lowercase()
        Box(
            Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)
        ) {
            if (!f.isDirectory && (ext in IMG || ext in VID)) {
                AsyncImage(f, null, imageLoader = loader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (f.isDirectory) {
                FolderPreview(f, loader)
            } else {
                Text("📄", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
            }
            Text(
                f.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0x99000000)).padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }

    @Composable
    fun FolderPreview(dir: File, loader: ImageLoader) {
        val previews by produceState(emptyList<File>(), dir) {
            value = withContext(Dispatchers.IO) { sample(dir) }
        }
        if (previews.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("📁", fontSize = 40.sp) }
        } else {
            Column(Modifier.fillMaxSize()) {
                for (r in 0..1) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0..1) {
                            val p = previews.getOrNull(r * 2 + c)
                            Box(Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)) {
                                if (p != null) AsyncImage(p, null, imageLoader = loader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isMedia(f: File) = f.isFile && f.extension.lowercase().let { it in IMG || it in VID }

    private fun sample(dir: File): List<File> {
        val out = ArrayList<File>()
        val kids = dir.listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: return out
        out.addAll(kids.filter { isMedia(it) }.take(4))
        if (out.size < 4) {
            for (k in kids.filter { it.isDirectory }) {
                k.listFiles()?.filter { isMedia(it) }?.sortedBy { it.name.lowercase() }?.take(4 - out.size)?.let { out.addAll(it) }
                if (out.size >= 4) break
            }
        }
        return out
    }

    private fun open(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fp", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
