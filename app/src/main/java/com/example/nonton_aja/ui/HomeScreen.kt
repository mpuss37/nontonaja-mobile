package com.example.nonton_aja.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.nonton_aja.data.CoilConfig
import com.example.nonton_aja.data.SearchItem
import com.example.nonton_aja.ui.theme.BgDark
import com.example.nonton_aja.ui.theme.RedPrimary
import com.example.nonton_aja.ui.theme.SurfaceVariant
import com.example.nonton_aja.ui.theme.TextGray
import com.example.nonton_aja.ui.theme.NontonajaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFilmClick: (SearchItem) -> Unit,
    onSearchClick: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val imageLoader = remember { CoilConfig.imageLoader(context) }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text("NontonAja", color = RedPrimary, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        },
        containerColor = BgDark
    ) { padding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            }
            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${state.error}", color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.loadHome() }) { Text("Retry") }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    state = rememberLazyListState()
                ) {
                    state.hero?.let { hero ->
                        item(key = "hero") { HeroBanner(hero = hero, imageLoader = imageLoader, onClick = { onFilmClick(hero) }) }
                    }

                    if (state.trending.isNotEmpty()) {
                        item(key = "trending_header") { SectionHeader("Trending Now") }
                        item(key = "trending_row") { HorizontalPosterRow(items = state.trending, imageLoader = imageLoader, onClick = onFilmClick) }
                    }

                    if (state.popularMovies.isNotEmpty()) {
                        item(key = "popular_movies_header") { SectionHeader("Popular Movies") }
                        item(key = "popular_movies_row") { HorizontalPosterRow(items = state.popularMovies, imageLoader = imageLoader, onClick = onFilmClick) }
                    }

                    if (state.popularTv.isNotEmpty()) {
                        item(key = "popular_tv_header") { SectionHeader("Popular TV Shows") }
                        item(key = "popular_tv_row") { HorizontalPosterRow(items = state.popularTv, imageLoader = imageLoader, onClick = onFilmClick) }
                    }

                    if (state.newReleases.isNotEmpty()) {
                        item(key = "new_releases_header") { SectionHeader("New Releases") }
                        item(key = "new_releases_row") { HorizontalPosterRow(items = state.newReleases, imageLoader = imageLoader, onClick = onFilmClick) }
                    }

                    if (state.topRated.isNotEmpty()) {
                        item(key = "top_rated_header") { SectionHeader("Top Rated") }
                        item(key = "top_rated_row") { HorizontalPosterRow(items = state.topRated, imageLoader = imageLoader, onClick = onFilmClick) }
                    }

                    item(key = "bottom_spacer") { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(hero: SearchItem, imageLoader: coil.ImageLoader, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(hero.image)
                .size(780, 420)
                .build(),
            imageLoader = imageLoader,
            contentDescription = hero.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BgDark),
                        startY = 150f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = hero.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (hero.year.isNotEmpty()) {
                Text(text = hero.year, color = TextGray, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(4.dp))
                Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "View All >", color = RedPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun HorizontalPosterRow(
    items: List<SearchItem>,
    imageLoader: coil.ImageLoader,
    onClick: (SearchItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            PosterCard(item = item, imageLoader = imageLoader, onClick = { onClick(item) })
        }
    }
}

@Composable
private fun PosterCard(item: SearchItem, imageLoader: coil.ImageLoader, onClick: () -> Unit) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(item.image)
            .size(300, 450)
            .build(),
        imageLoader = imageLoader,
        contentDescription = item.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(130.dp)
            .height(195.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(SurfaceVariant)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF141414)
@Composable
private fun HomePreview() {
    NontonajaTheme {
        HomeScreen(onFilmClick = {}, onSearchClick = {})
    }
}
