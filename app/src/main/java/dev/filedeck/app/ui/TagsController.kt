package dev.filedeck.app.ui

import androidx.exifinterface.media.ExifInterface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.filedeck.app.ExifTags
import dev.filedeck.app.FileEntry
import dev.filedeck.app.Fs
import dev.filedeck.app.R
import dev.filedeck.app.Str
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Редактор тегов: название и дата изменения у любого файла, EXIF у фото. */
class TagsController(val vm: AppViewModel, val entry: FileEntry) {
    var hasExif by mutableStateOf(!entry.dir && ExifTags.supports(entry.name))
    var loaded by mutableStateOf(!hasExif)
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")

    var name by mutableStateOf(entry.name)
    var mtime by mutableStateOf(ExifTags.formatUser(entry.mtime))
    var date by mutableStateOf("")
    var gps by mutableStateOf("")
    var orientation by mutableStateOf("")
    var syncMtime by mutableStateOf(false)
    val values = mutableStateMapOf<String, String>()

    private val originalMtime = ExifTags.formatUser(entry.mtime)
    private var originalDate = ""
    private var originalGps = ""
    private var originalOrientation = ""

    init {
        if (hasExif) {
            vm.viewModelScope.launch {
                try {
                    val d = withContext(Dispatchers.IO) { ExifTags.read(entry.path) }
                    date = d.date
                    originalDate = d.date
                    gps = d.gps
                    originalGps = d.gps
                    orientation = d.orientation
                    originalOrientation = d.orientation
                    values.putAll(d.values)
                } catch (e: Exception) {
                    vm.toast(e.message ?: Str.s(R.string.tags_read_failed), true)
                    hasExif = false
                }
                loaded = true
            }
        }
    }

    fun close() {
        vm.overlays.removeAll { it is Overlay.Tags && it.c === this }
    }

    fun save() {
        if (busy) return
        error = ""
        var dateMs: Long? = null
        if (hasExif && date.isNotBlank()) {
            dateMs = ExifTags.parseUser(date)
            if (dateMs == null) {
                vm.toast(Str.s(R.string.tags_bad_date), true)
                return
            }
        }
        val clearDate = hasExif && date.isBlank() && originalDate.isNotBlank()
        var gpsPair: Pair<Double, Double>? = null
        if (hasExif && gps.isNotBlank()) {
            gpsPair = ExifTags.parseGps(gps)
            if (gpsPair == null) {
                vm.toast(Str.s(R.string.tags_bad_gps), true)
                return
            }
        }
        if (hasExif && orientation.isNotBlank() && orientation.trim() != originalOrientation &&
            orientation.trim() !in listOf("1", "2", "3", "4", "5", "6", "7", "8")) {
            error = Str.s(R.string.tags_bad_orientation)
            vm.toast(error, true)
            return
        }
        val clearGps = hasExif && gps.isBlank() && originalGps.isNotBlank()
        var newMtime: Long? = null
        if (syncMtime && dateMs != null) {
            newMtime = dateMs
        } else if (mtime.trim() != originalMtime) {
            newMtime = ExifTags.parseUser(mtime)
            if (newMtime == null) {
                vm.toast(Str.s(R.string.tags_bad_mtime), true)
                return
            }
        }
        val finalMtime = newMtime
        val snapshot = values.toMap()
        val newName = name.trim()
        val orient = orientation
        val exif = hasExif
        busy = true
        vm.viewModelScope.launch {
            try {
                val newPath = withContext(Dispatchers.IO) {
                    var p = entry.path
                    if (exif) ExifTags.write(p, snapshot, dateMs, clearDate, gpsPair, clearGps, orient)
                    if (finalMtime != null) File(p).setLastModified(finalMtime)
                    if (newName.isNotEmpty() && newName != entry.name) p = Fs.rename(p, newName)
                    p
                }
                vm.toast(Str.s(R.string.tags_saved))
                vm.onFileChanged(entry.path, newPath)
                close()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("DL-File", "tags save failed", e)
                error = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                vm.toast(error, true)
            } finally {
                busy = false
            }
        }
    }

    companion object {
        /** Подписи полей в порядке показа. */
        fun labelOf(tag: String): Int = when (tag) {
            ExifInterface.TAG_MAKE -> R.string.tag_make
            ExifInterface.TAG_MODEL -> R.string.tag_model
            ExifInterface.TAG_LENS_MODEL -> R.string.tag_lens
            ExifInterface.TAG_SOFTWARE -> R.string.tag_software
            ExifInterface.TAG_ARTIST -> R.string.tag_artist
            ExifInterface.TAG_COPYRIGHT -> R.string.tag_copyright
            ExifInterface.TAG_IMAGE_DESCRIPTION -> R.string.tag_description
            ExifInterface.TAG_USER_COMMENT -> R.string.tag_comment
            ExifInterface.TAG_F_NUMBER -> R.string.tag_fnumber
            ExifInterface.TAG_EXPOSURE_TIME -> R.string.tag_exposure
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY -> R.string.tag_iso
            else -> R.string.tag_focal
        }
    }
}
