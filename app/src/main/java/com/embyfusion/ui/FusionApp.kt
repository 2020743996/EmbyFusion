@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.embyfusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.embyfusion.data.QualityScorer
import com.embyfusion.model.AddServerRequest
import com.embyfusion.model.AggregatedMovie
import com.embyfusion.model.EmbyServer
import com.embyfusion.model.SourceVariant
import com.embyfusion.ui.theme.FusionBackground
import com.embyfusion.ui.theme.FusionGreen
import com.embyfusion.ui.theme.FusionSurfaceHigh
import java.util.Locale

private enum class Section { LIBRARY, SOURCES }

@Composable fun FusionApp(vm: FusionViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(Section.LIBRARY) }
    var showAdd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    state.player?.let { request ->
        BackHandler { vm.stopPlayback() }
        PlayerScreen(
            request = request,
            onBack = vm::stopPlayback,
            onStarted = { vm.playbackStarted(request, it) },
            onProgress = { position, paused, event -> vm.playbackProgress(request, position, paused, event) },
            onStopped = { vm.playbackStopped(request, it) },
            onFailure = { vm.playbackFailed(request, it) }
        )
        return
    }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
    if (showAdd) AddServerDialog(state.mutatingServer, onDismiss = { showAdd = false }) {
        vm.addServer(it) { showAdd = false }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            TabletShell(state, section, { section = it }, vm, { showAdd = true }, snackbar)
        } else {
            PhoneShell(state, section, { section = it }, vm, { showAdd = true }, snackbar)
        }
    }
}

@Composable private fun TabletShell(
    state: FusionUiState, section: Section, selectSection: (Section) -> Unit,
    vm: FusionViewModel, addServer: () -> Unit, snackbar: SnackbarHostState
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            NavigationRail(containerColor = Color(0xFF0D1118), modifier = Modifier.padding(top = 24.dp)) {
                Spacer(Modifier.height(24.dp))
                RailItem(section == Section.LIBRARY, Icons.Default.Home, "片库") { selectSection(Section.LIBRARY) }
                RailItem(section == Section.SOURCES, Icons.Default.Dns, "源") { selectSection(Section.SOURCES) }
                Spacer(Modifier.weight(1f))
                IconButton(addServer) { Icon(Icons.Default.Add, "添加服务器") }
                Spacer(Modifier.height(24.dp))
            }
            if (section == Section.SOURCES) {
                ServerScreen(state.servers, state.loading, addServer, vm::removeServer, vm::refresh, Modifier.weight(1f))
            } else {
                LibraryPane(state, vm, addServer, Modifier.weight(1f))
                state.selected?.let { movie ->
                    HorizontalDivider(Modifier.fillMaxHeight().width(1.dp), color = Color.White.copy(alpha = .08f))
                    MovieDetail(movie, { vm.play(movie, it) }, { vm.select(null) }, true, Modifier.width(430.dp))
                }
            }
        }
    }
}

@Composable private fun PhoneShell(
    state: FusionUiState, section: Section, selectSection: (Section) -> Unit,
    vm: FusionViewModel, addServer: () -> Unit, snackbar: SnackbarHostState
) {
    if (state.selected != null && section == Section.LIBRARY) {
        val movie = state.selected
        BackHandler { vm.select(null) }
        MovieDetail(movie, { vm.play(movie, it) }, { vm.select(null) }, false, Modifier.fillMaxSize())
        return
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                BottomItem(section == Section.LIBRARY, Icons.Default.Home, "片库") { selectSection(Section.LIBRARY) }
                BottomItem(section == Section.SOURCES, Icons.Default.Dns, "播放源") { selectSection(Section.SOURCES) }
            }
        }
    ) { padding ->
        if (section == Section.SOURCES) {
            ServerScreen(state.servers, state.loading, addServer, vm::removeServer, vm::refresh, Modifier.padding(padding))
        } else LibraryPane(state, vm, addServer, Modifier.padding(padding))
    }
}

@Composable private fun LibraryPane(state: FusionUiState, vm: FusionViewModel, addServer: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(FusionBackground)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EMBY FUSION", letterSpacing = 2.sp, fontWeight = FontWeight.Black, color = FusionGreen)
                Text("${state.movies.size} 部影片 · ${state.servers.size} 个源", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(vm::refresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "刷新") }
            IconButton(addServer) { Icon(Icons.Default.Add, "添加服务器") }
        }
        OutlinedTextField(
            value = state.query, onValueChange = vm::setQuery, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索聚合片库") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp)
        )
        if (state.warnings.isNotEmpty()) {
            Text("${state.warnings.size} 个源暂时不可用，其余片库已正常加载", color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("正在分页读取并聚合 ${state.servers.size} 个 Emby 源…", fontWeight = FontWeight.SemiBold)
                    Text("大型片库首次同步可能需要片刻", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            state.servers.isEmpty() -> EmptyLibrary(addServer)
            state.filteredMovies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有找到影片", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> MovieGrid(state.filteredMovies, vm::select)
        }
    }
}

@Composable private fun MovieGrid(movies: List<AggregatedMovie>, onMovie: (AggregatedMovie) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(145.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)
    ) { items(movies, key = { it.key }) { MovieCard(it, onMovie) } }
}

@Composable private fun MovieCard(movie: AggregatedMovie, onMovie: (AggregatedMovie) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Transparent)) {
        Card(onClick = { onMovie(movie) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(movie.posterUrl, movie.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(.85f)))))
                Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                    Badge(QualityScorer.badge(movie.bestVariant.video), FusionGreen, Color.Black)
                    QualityScorer.hdrBadge(movie.bestVariant.video)?.let { Badge(it, Color(0xFFFFC857), Color.Black) }
                }
                if (movie.variants.size > 1) Badge("${movie.variants.size} 源", Color.Black.copy(.7f), Color.White, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Text(movie.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(listOfNotNull(movie.year?.toString(), movie.bestVariant.serverName).joinToString(" · "),
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable private fun MovieDetail(
    movie: AggregatedMovie, play: (SourceVariant) -> Unit, back: () -> Unit,
    tablet: Boolean, modifier: Modifier
) {
    val best = movie.bestVariant
    LazyColumn(modifier.background(FusionBackground), contentPadding = PaddingValues(bottom = 36.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(if (tablet) 230.dp else 270.dp)) {
                AsyncImage(movie.backdropUrl ?: movie.posterUrl, movie.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.08f), FusionBackground))))
                IconButton(back, Modifier.padding(WindowInsets.statusBars.asPaddingValues()).align(Alignment.TopStart).background(Color.Black.copy(.45f), RoundedCornerShape(50))) {
                    Icon(Icons.Default.ArrowBack, "返回")
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text(movie.title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(listOfNotNull(movie.year?.toString(), formatRuntime(movie.runtimeTicks), movie.communityRating?.let { "★ %.1f".format(it) }).joinToString("  ·  "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Button(onClick = { play(best) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(
                        if (movie.resumePositionTicks > 0) "继续播放 · ${formatPosition(movie.resumePositionTicks)}"
                        else "播放最高规格 · ${variantHeadline(best)}"
                    )
                }
                if (movie.overview.isNotBlank()) {
                    Spacer(Modifier.height(22.dp)); Text(movie.overview, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(26.dp)); Text("选择播放源", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("已按画质、HDR、编码、码率和音轨综合排序", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
            }
        }
        items(movie.variants, key = { "${it.serverId}:${it.mediaSourceId}" }) { variant ->
            SourceRow(variant, variant == best) { play(variant) }
        }
    }
}

@Composable private fun SourceRow(variant: SourceVariant, best: Boolean, play: () -> Unit) {
    Card(onClick = play, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = if (best) FusionGreen.copy(.10f) else FusionSurfaceHigh),
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(variant.serverName, fontWeight = FontWeight.Bold)
                    if (best) { Spacer(Modifier.width(7.dp)); Icon(Icons.Default.CheckCircle, "最佳", tint = FusionGreen, modifier = Modifier.size(17.dp)) }
                }
                Text(variantHeadline(variant), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(sourceDetails(variant), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.PlayArrow, "播放", tint = if (best) FusionGreen else Color.White)
        }
    }
}

@Composable private fun ServerScreen(
    servers: List<EmbyServer>, loading: Boolean, add: () -> Unit, remove: (String) -> Unit,
    refresh: () -> Unit, modifier: Modifier
) {
    Column(modifier.fillMaxSize().background(FusionBackground).padding(top = 18.dp)) {
        TopAppBar(title = { Column { Text("播放源"); Text("多个 Emby 服务器统一为一个片库", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            actions = { IconButton(refresh) { Icon(Icons.Default.Refresh, "刷新") }; IconButton(add) { Icon(Icons.Default.Add, "添加") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        if (servers.isEmpty()) EmptyLibrary(add) else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            items(servers, key = { it.id }) { server -> ServerCard(server, remove) }
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable private fun ServerCard(server: EmbyServer, remove: (String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("移除 ${server.name}？") },
        text = { Text("只会删除本机保存的连接，不会修改 Emby 服务器。") },
        confirmButton = { TextButton({ confirm = false; remove(server.id) }) { Text("移除") } },
        dismissButton = { TextButton({ confirm = false }) { Text("取消") } })
    Card(colors = CardDefaults.cardColors(containerColor = FusionSurfaceHigh), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = FusionGreen.copy(.13f), modifier = Modifier.size(46.dp)) {
                Icon(Icons.Default.SettingsEthernet, null, tint = FusionGreen, modifier = Modifier.padding(11.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(server.name, fontWeight = FontWeight.Bold)
                Text(server.baseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ confirm = true }) { Icon(Icons.Default.DeleteOutline, "移除") }
        }
    }
}

@Composable private fun EmptyLibrary(add: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Dns, null, tint = FusionGreen, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(18.dp)); Text("连接你的第一个 Emby", fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Text("添加多个服务器后，同一影片会自动合并并选出最高规格。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            FilledTonalButton(add) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("添加服务器") }
        }
    }
}

@Composable private fun AddServerDialog(loading: Boolean, onDismiss: () -> Unit, submit: (AddServerRequest) -> Unit) {
    var name by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!loading) onDismiss() }, title = { Text("添加 Emby 服务器") },
        text = { Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("显示名称（可选）") }, singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text("服务器地址") }, placeholder = { Text("https://emby.example.com") }, singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text("用户名") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        } },
        confirmButton = { Button(onClick = { submit(AddServerRequest(name, url, username, password)) }, enabled = !loading && url.isNotBlank() && username.isNotBlank()) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("连接")
        } }, dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("取消") } })
}

@Composable private fun Badge(text: String, color: Color, content: Color, modifier: Modifier = Modifier) {
    Text(text, color = content, fontWeight = FontWeight.Black, fontSize = 9.sp,
        modifier = modifier.background(color, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
}

@Composable private fun RailItem(selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) =
    NavigationRailItem(selected, click, { Icon(icon, label) }, label = { Text(label) })

@Composable private fun RowScope.BottomItem(selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, click: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = click,
        icon = { Icon(icon, label) },
        label = { Text(label) }
    )
}

private fun variantHeadline(v: SourceVariant): String = listOfNotNull(
    QualityScorer.badge(v.video), QualityScorer.hdrBadge(v.video), v.video?.codec?.uppercase(Locale.ROOT),
    v.audio?.codec?.uppercase(Locale.ROOT), v.audio?.channels?.let { "${it}CH" }
).joinToString(" · ")

private fun sourceDetails(v: SourceVariant): String = listOfNotNull(
    v.totalBitrate.takeIf { it > 0 }?.let { "%.1f Mbps".format(it / 1_000_000.0) },
    v.sizeBytes.takeIf { it > 0 }?.let { "%.1f GB".format(it / 1_073_741_824.0) },
    v.container.uppercase(Locale.ROOT), v.audio?.displayTitle
).joinToString(" · ")

private fun formatRuntime(ticks: Long): String? = ticks.takeIf { it > 0 }?.let {
    val minutes = it / 600_000_000L; "${minutes / 60}小时${minutes % 60}分"
}

private fun formatPosition(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
