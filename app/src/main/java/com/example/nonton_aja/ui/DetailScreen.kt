package com.example.nonton_aja.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.nonton_aja.data.SearchItem
import com.example.nonton_aja.ui.theme.BgDark
import com.example.nonton_aja.ui.theme.RedPrimary
import com.example.nonton_aja.ui.theme.SurfaceVariant
import com.example.nonton_aja.ui.theme.TextGray
import com.example.nonton_aja.ui.theme.NontonajaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: SearchItem,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = BgDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hero Banner
            item { DetailHeroBanner(item) }

            // Action Buttons
            item { DetailActionButtons(item = item, onPlay = onPlay) }

            // Info Section
            item { DetailInfoSection(item) }

            // Synopsis
            item { DetailSynopsis(item) }

            // Genre Tags
            item { DetailGenreTags(item) }

            // Quality & Source
            item { DetailTechInfo(item) }

            // Bottom spacing
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun DetailHeroBanner(item: SearchItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.image)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BgDark),
                        startY = 100f
                    )
                )
        )
    }
}

@Composable
private fun DetailActionButtons(item: SearchItem, onPlay: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (item.year.isNotEmpty()) {
            Text(
                text = item.year,
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(4.dp))
                Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder,
                modifier = Modifier.weight(1f)
            ) {
                Text("My List", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailInfoSection(item: SearchItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (item.source.isNotEmpty()) {
            InfoRow(label = "Source", value = item.source.uppercase())
        }
        if (item.type.isNotEmpty()) {
            InfoRow(label = "Type", value = item.type.replaceFirstChar { it.uppercase() })
        }
        if (item.year.isNotEmpty()) {
            InfoRow(label = "Year", value = item.year)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextGray, fontSize = 14.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailSynopsis(item: SearchItem) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Synopsis",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        val text = "Film ${item.title} (${item.year}) tersedia di ${item.source.uppercase()}. " +
                "Nonton sekarang dengan kualitas terbaik dan subtitle Indonesia."

        Text(
            text = if (expanded) text else text.take(150) + if (text.length > 150) "..." else "",
            color = TextGray,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.clickable { expanded = !expanded }
        )

        if (text.length > 150) {
            Text(
                text = if (expanded) "Show Less" else "Read More",
                color = RedPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}

@Composable
private fun DetailGenreTags(item: SearchItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Genres",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AssistChip(
                onClick = { },
                label = { Text(item.source.uppercase(), color = Color.White, fontSize = 12.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceVariant)
            )
            AssistChip(
                onClick = { },
                label = { Text(item.type.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 12.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceVariant)
            )
        }
    }
}

@Composable
private fun DetailTechInfo(item: SearchItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Available Quality",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityBadge("480p")
            QualityBadge("720p")
            QualityBadge("1080p")
        }
    }
}

@Composable
private fun QualityBadge(quality: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = SurfaceVariant
    ) {
        Text(
            text = quality,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun DetailPreview() {
    NontonajaTheme {
        DetailScreen(
            item = SearchItem(id = "1", title = "Contoh Film", year = "2024", image = "", type = "movie", source = "lk21"),
            onBack = { },
            onPlay = { }
        )
    }
}
