package dev.filedeck.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.filedeck.app.R
import dev.filedeck.app.SortBy
import dev.filedeck.app.Str
import dev.filedeck.app.compareNames
import dev.filedeck.app.extOf

/** Выбранная сортировка: по чему и в каком порядке. */
data class SortSpec(val by: SortBy = SortBy.NAME, val desc: Boolean = false)

/** Сравнение по выбранному полю с учётом порядка (без разделения папок и файлов). */
fun <T> sortComparator(spec: SortSpec, name: (T) -> String, size: (T) -> Long, time: (T) -> Long): Comparator<T> {
    val sign = if (spec.desc) -1 else 1
    return Comparator<T> { a, b ->
        val c = when (spec.by) {
            SortBy.NAME -> compareNames(name(a), name(b))
            SortBy.SIZE -> size(a).compareTo(size(b))
            SortBy.DATE -> time(a).compareTo(time(b))
            SortBy.TYPE -> compareNames(extOf(name(a)), extOf(name(b)))
        }
        (if (c != 0) c else compareNames(name(a), name(b))) * sign
    }
}

/** Универсальная сортировка для списков, которые не FileEntry (корзина, архив, очистка). Папки сверху, если isDir их выделяет. */
fun <T> sortWith(
    list: List<T>,
    spec: SortSpec,
    isDir: (T) -> Boolean,
    name: (T) -> String,
    size: (T) -> Long,
    time: (T) -> Long,
): List<T> {
    val cmp = sortComparator(spec, name, size, time)
    return list.sortedWith { a, b ->
        val da = isDir(a)
        val db = isDir(b)
        if (da != db) (if (da) -1 else 1) else cmp.compare(a, b)
    }
}

/** Лист «Сортировка»: поле и порядок. Каждое изменение применяется сразу. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(spec: SortSpec, options: List<SortBy>, onChange: (SortSpec) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = fd.surface2) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            NameText(Str.s(R.string.s_sortirovka), Modifier.padding(horizontal = 20.dp), size = 18, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            options.forEach { m ->
                ChoiceRow(m.label, null, spec.by == m) {
                    onChange(spec.copy(by = m))
                    onDismiss()
                }
            }
            RowDivider()
            SwitchRow(
                Str.s(R.string.s_v_obratnom_poryadke), Str.s(R.string.s_novye_i_bolshie_fayly_sverhu), spec.desc,
            ) { v -> onChange(spec.copy(desc = v)) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Кнопка сортировки вместе с её листом: одна и та же на каждом экране со списком. */
@Composable
fun SortAction(spec: SortSpec, onChange: (SortSpec) -> Unit, options: List<SortBy> = SortBy.entries) {
    var open by remember { mutableStateOf(false) }
    FdIconButton(Icons2.sort, Str.s(R.string.s_sortirovka), { open = true })
    if (open) SortSheet(spec, options, onChange) { open = false }
}
