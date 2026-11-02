package dev.filedeck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.filedeck.app.ExifTags
import dev.filedeck.app.R
import dev.filedeck.app.Str

@Composable
private fun TagField(label: String, value: String, placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { FdText(label) },
        placeholder = if (placeholder != null) { { FdText(placeholder) } } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun TagsScreen(c: TagsController) {
    Column(Modifier.fillMaxSize().background(fd.surface).imePadding()) {
        FdBar(c.entry.name, onBack = { c.close() })
        if (!c.loaded) {
            Spinner(Modifier.weight(1f))
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TagField(Str.s(R.string.tags_name), c.name) { c.name = it }
                TagField(Str.s(R.string.tags_mtime), c.mtime, Str.s(R.string.tags_date_hint)) { c.mtime = it }
                if (!c.hasExif) {
                    MetaText(Str.s(R.string.tags_no_exif), size = 13)
                } else {
                    SectionTitle(Str.s(R.string.tags_section_photo))
                    TagField(Str.s(R.string.tags_date), c.date, Str.s(R.string.tags_date_hint)) { c.date = it }
                    SwitchRow(Str.s(R.string.tags_sync_mtime), null, c.syncMtime) { c.syncMtime = it }
                    ExifTags.TEXT_TAGS.forEach { tag ->
                        TagField(Str.s(TagsController.labelOf(tag)), c.values[tag] ?: "") { c.values[tag] = it }
                    }
                    TagField(Str.s(R.string.tags_gps), c.gps, "55.751244, 37.618423") { c.gps = it }
                    TagField(Str.s(R.string.tags_orientation), c.orientation) { c.orientation = it }
                }
            }
            BottomAction { PrimaryButton(Str.s(R.string.tags_save), { c.save() }, Modifier.fillMaxWidth(), enabled = !c.busy) }
        }
    }
}
