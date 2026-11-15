package com.philpf.nova.ui

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.philpf.nova.ui.theme.Space
import com.philpf.nova.widget.NowPlayingWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The widget's two layouts, drawn in the app (the debug build's screenshots). */
@Composable
fun WidgetPreview(info: NowPlayingWidget.Info) {
    val context = LocalContext.current
    val views = produceState(initialValue = emptyList(), info) {
        value = withContext(Dispatchers.IO) { NowPlayingWidget.preview(context, info) }
    }.value
    Box(Modifier.fillMaxSize().padding(Space.margin), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.section), horizontalAlignment = Alignment.CenterHorizontally) {
            views.forEachIndexed { i, rv ->
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        frame.removeAllViews()
                        frame.addView(rv.apply(frame.context, frame))
                    },
                    modifier = if (i == 0) Modifier.size(170.dp) else Modifier.width(350.dp).height(160.dp),
                )
            }
        }
    }
}
