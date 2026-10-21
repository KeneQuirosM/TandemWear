package app.tandemwear.watch.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.tandemwear.watch.ui.theme.TandemType

/**
 * Standard screen: a scaling list inside ScreenScaffold (time text, scroll indicator,
 * round-screen padding). Rotary input scrolls it.
 */
@Composable
fun TandemList(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            content = content
        )
    }
}

/** Screen title, announced as a heading by TalkBack. */
@Composable
fun ScreenTitle(title: String) {
    ListHeader(modifier = Modifier.semantics { heading() }) {
        Text(title, fontSize = TandemType.title, textAlign = TextAlign.Center)
    }
}
