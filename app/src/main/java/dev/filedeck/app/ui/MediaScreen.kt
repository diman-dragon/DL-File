package dev.filedeck.app.ui

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import dev.filedeck.app.FileEntry
import dev.filedeck.app.FileKind
import dev.filedeck.app.R
import dev.filedeck.app.Str
import dev.filedeck.app.formatDate
import dev.filedeck.app.formatSize
import dev.filedeck.app.kind
import dev.filedeck.app.meta
import java.io.File
import kotlinx.coroutines.delay

/** Просмотрщик фото и видео: листание свайпом влево/вправо, масштаб щипком и двойным касанием. */
@Composable
fun MediaScreen(vm: AppViewModel, o: Overlay.Media) {
    val items = o.items
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { vm.closeOverlay(o) }
        return
    }
    val pager = rememberPagerState(initialPage = o.start.coerceIn(0, items.lastIndex), pageCount = { o.items.size })
    var chrome by remember { mutableStateOf(true) }
    val cur = o.items.getOrNull(pager.currentPage)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { o.items.getOrNull(it)?.path ?: it }) { page ->
            val e = o.items.getOrNull(page)
            if (e != null) {
                if (e.kind() == FileKind.Video) {
                    VideoPage(e, pager.currentPage == page, chrome) { chrome = !chrome }
                } else {
                    ZoomableImage(e.path) { chrome = !chrome }
                }
            }
        }
        if (chrome && cur != null) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding().heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FdIconButton(Icons2.back, Str.s(R.string.s_nazad), { vm.closeOverlay(o) }, tint = Color.White)
                Column(Modifier.weight(1f)) {
                    NameText(cur.name, size = 16, color = Color.White)
                    FdText(
                        meta(cur.size.takeIf { it > 0 }?.let { formatSize(it) },
                            cur.mtime.takeIf { it > 0 }?.let { formatDate(it, true) }) +
                            "  ${pager.currentPage + 1} / ${o.items.size}",
                        color = Color(0xFFCCCCCC), fontSize = 12.sp,
                    )
                }
                FdIconButton(Icons2.tag, Str.s(R.string.tags_menu), { vm.editTags(cur) }, tint = Color.White)
                FdIconButton(Icons2.share, Str.s(R.string.action_share), { vm.sharePath(cur.path) }, tint = Color.White)
                FdIconButton(Icons2.external, Str.s(R.string.s_otkryt_v_prilozhenii), { vm.openPathExternal(cur.path) }, tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(path: String, onTap: () -> Unit) {
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var ox by remember(path) { mutableFloatStateOf(0f) }
    var oy by remember(path) { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        if (scale > 1f) {
            val mx = box.width * (scale - 1f) / 2f
            val my = box.height * (scale - 1f) / 2f
            ox = (ox + pan.x).coerceIn(-mx, mx)
            oy = (oy + pan.y).coerceIn(-my, my)
        } else {
            ox = 0f
            oy = 0f
        }
    }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) { scale = 1f; ox = 0f; oy = 0f } else scale = 2.5f },
                )
            }
            // Одним пальцем без увеличения жест не забираем — его получает HorizontalPager (листание).
            // Увеличенную картинку тянем, пока не упрёмся в край, дальше жест снова уходит листалке.
            .transformable(
                state = state,
                canPan = { pan ->
                    if (scale <= 1f) {
                        false
                    } else {
                        val mx = box.width * (scale - 1f) / 2f
                        !((pan.x > 0 && ox >= mx - 1f) || (pan.x < 0 && ox <= -mx + 1f))
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(path), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
        )
    }
}

private fun mmss(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun VideoPage(e: FileEntry, active: Boolean, chrome: Boolean, onTap: () -> Unit) {
    if (!active) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons2.play, null, tint = Color(0x99FFFFFF), modifier = Modifier.size(64.dp))
        }
        return
    }
    var view by remember(e.path) { mutableStateOf<VideoView?>(null) }
    var playing by remember(e.path) { mutableStateOf(false) }
    var pos by remember(e.path) { mutableIntStateOf(0) }
    var dur by remember(e.path) { mutableIntStateOf(0) }
    var seeking by remember(e.path) { mutableStateOf(false) }
    LaunchedEffect(e.path) {
        while (true) {
            val v = view
            if (v != null) {
                playing = v.isPlaying
                if (!seeking) pos = v.currentPosition
                if (dur <= 0) dur = v.duration.coerceAtLeast(0)
            }
            delay(250)
        }
    }
    DisposableEffect(e.path) { onDispose { view?.stopPlayback() } }
    fun toggle() {
        val v = view ?: return
        if (v.isPlaying) v.pause() else v.start()
        playing = v.isPlaying
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                val v = VideoView(c)
                v.setOnPreparedListener { mp ->
                    dur = mp.duration.coerceAtLeast(0)
                    v.seekTo(1)
                }
                v.setOnCompletionListener {
                    playing = false
                    pos = 0
                    v.seekTo(1)
                }
                v.setVideoPath(e.path)
                view = v
                FrameLayout(c).apply {
                    addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            if (!playing) {
                FdIconButton(Icons2.play, Str.s(R.string.media_play), { toggle() }, tint = Color.White)
            }
        }
        if (chrome) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).navigationBarsPadding()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FdIconButton(if (playing) Icons2.pause else Icons2.play,
                    Str.s(if (playing) R.string.media_pause else R.string.media_play), { toggle() }, tint = Color.White)
                FdText(mmss(pos), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                Slider(
                    value = pos.toFloat().coerceIn(0f, maxOf(1, dur).toFloat()),
                    onValueChange = { seeking = true; pos = it.toInt() },
                    onValueChangeFinished = { view?.seekTo(pos); seeking = false },
                    valueRange = 0f..maxOf(1, dur).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = fd.manila, activeTrackColor = fd.manila),
                    modifier = Modifier.weight(1f),
                )
                FdText(mmss(dur), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
