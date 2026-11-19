package com.realme.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.realme.core.model.Post
import com.realme.core.model.PostStatus
import com.realme.R
import java.io.File

@Composable
fun StatusBadge(status: PostStatus, modifier: Modifier = Modifier) {
    if (status == PostStatus.ON_TIME) return
    if (status == PostStatus.LATE) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "遅刻した投稿",
            modifier = modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        return
    }
    val color = MaterialTheme.colorScheme.tertiary
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = CircleShape,
    ) {
        Text(
            text = "PRIVACY",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun RealMeLogo(modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painterResource(R.drawable.realme_logo),
        contentDescription = null,
        modifier = modifier,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

@Composable
fun ExplanationButton(title: String, message: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Default.MoreVert, contentDescription = "$title の説明")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("閉じる") } },
        )
    }
}

@Composable
fun ProfileAvatar(name: String, modifier: Modifier = Modifier) {
    val colors = listOf(Mint, Lavender, Color(0xFF7A6143), Color(0xFF4D6673))
    val color = colors[(name.hashCode().toUInt().toInt() and Int.MAX_VALUE) % colors.size]
    Box(
        modifier = modifier.size(44.dp).background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun PostImage(post: Post, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(DarkSurfaceVariant)) {
        if (post.imagePath == null) {
            DemoArtwork(post = post, modifier = Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = File(post.imagePath),
                contentDescription = "${post.caption.ifBlank { "投稿画像" }}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    ImageError(message = "画像を読み込めません")
                },
            )
        }
    }
}

@Composable
private fun DemoArtwork(post: Post, modifier: Modifier = Modifier) {
    val palettes = listOf(Color(0xFF54625C), Color(0xFF666259), Color(0xFF615D65))
    val palette = palettes[(post.id.hashCode().toUInt().toInt() and Int.MAX_VALUE) % palettes.size]
    Box(
        modifier = modifier.background(palette),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RealMeLogo(
                modifier = Modifier.size(72.dp),
                tint = Color.White.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (post.status == PostStatus.PRIVACY) "PRIVATE MODE" else "GAME MOMENT",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "ローカルデモ画像",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ImageError(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RealMeLogo(
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            MaterialTheme.shapes.small,
        ).padding(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}
