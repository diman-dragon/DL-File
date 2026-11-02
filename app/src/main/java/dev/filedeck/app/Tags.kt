package dev.filedeck.app

import android.media.ExifInterface
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Чтение и запись EXIF-тегов изображений (JPEG, PNG, WebP, DNG) через системный ExifInterface. */
object ExifTags {
    private val EXTS = setOf("jpg", "jpeg", "png", "webp", "dng")

    fun supports(name: String): Boolean = extOf(name) in EXTS

    /** Текстовые теги, которые правятся как есть. */
    val TEXT_TAGS: List<String> = listOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
    )

    const val TAG_DATE = ExifInterface.TAG_DATETIME_ORIGINAL

    private fun userFmt() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { isLenient = false }
    private fun exifFmt() = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).apply { isLenient = false }

    /** Дата в виде «2024-05-01 14:30:00» (местное время). */
    fun formatUser(ms: Long): String = if (ms <= 0) "" else userFmt().format(Date(ms))

    /** Разбор «2024-05-01 14:30:00»; null, если формат неверный. */
    fun parseUser(s: String): Long? = try { userFmt().parse(s.trim())?.time } catch (e: Exception) { null }

    fun exifToUser(s: String?): String = try {
        if (s.isNullOrBlank()) "" else userFmt().format(exifFmt().parse(s.trim())!!)
    } catch (e: Exception) {
        s ?: ""
    }

    fun userToExif(ms: Long): String = exifFmt().format(Date(ms))

    private fun dms(v: Double): String {
        val a = abs(v)
        val d = a.toInt()
        val m = ((a - d) * 60).toInt()
        val s = ((a - d) * 60 - m) * 60
        return "$d/1,$m/1,${Math.round(s * 10000)}/10000"
    }

    private fun fromDms(s: String?, ref: String?): Double? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(",")
        if (parts.size != 3) return null
        fun rat(x: String): Double? {
            val p = x.trim().split("/")
            val n = p[0].toDoubleOrNull() ?: return null
            val d = if (p.size > 1) p[1].toDoubleOrNull() ?: return null else 1.0
            return if (d == 0.0) null else n / d
        }
        val deg = rat(parts[0]) ?: return null
        val min = rat(parts[1]) ?: return null
        val sec = rat(parts[2]) ?: return null
        val v = deg + min / 60 + sec / 3600
        return if (ref == "S" || ref == "W") -v else v
    }

    class Data(val date: String, val values: Map<String, String>, val gps: String, val orientation: String)

    @Throws(IOException::class)
    fun read(path: String): Data {
        val ex = ExifInterface(path)
        val values = LinkedHashMap<String, String>()
        for (t in TEXT_TAGS) values[t] = ex.getAttribute(t) ?: ""
        val lat = fromDms(ex.getAttribute(ExifInterface.TAG_GPS_LATITUDE), ex.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        val lon = fromDms(ex.getAttribute(ExifInterface.TAG_GPS_LONGITUDE), ex.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
        val gps = if (lat != null && lon != null) String.format(Locale.ROOT, "%.6f, %.6f", lat, lon) else ""
        return Data(
            exifToUser(ex.getAttribute(TAG_DATE)),
            values,
            gps,
            ex.getAttribute(ExifInterface.TAG_ORIENTATION) ?: "",
        )
    }

    /** Разбор «55.751244, 37.618423»; пустая строка — убрать координаты. null — формат неверный. */
    fun parseGps(s: String): Pair<Double, Double>? {
        val p = s.replace(';', ',').split(",")
        if (p.size != 2) return null
        val a = p[0].trim().toDoubleOrNull() ?: return null
        val b = p[1].trim().toDoubleOrNull() ?: return null
        return if (abs(a) <= 90 && abs(b) <= 180) a to b else null
    }

    /**
     * Записывает теги. Пустое значение удаляет тег. dateMs == null — дату съёмки не трогать.
     * Время изменения файла возвращается прежним (запись EXIF его бы сбросила).
     */
    @Throws(IOException::class)
    fun write(path: String, values: Map<String, String>, dateMs: Long?, clearDate: Boolean, gps: Pair<Double, Double>?,
        clearGps: Boolean, orientation: String) {
        val file = File(path)
        val before = file.lastModified()
        val ex = ExifInterface(path)
        for ((t, v) in values) ex.setAttribute(t, v.trim().ifEmpty { null })
        if (dateMs != null) {
            val s = userToExif(dateMs)
            ex.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, s)
            ex.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, s)
            ex.setAttribute(ExifInterface.TAG_DATETIME, s)
        } else if (clearDate) {
            ex.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
            ex.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
            ex.setAttribute(ExifInterface.TAG_DATETIME, null)
        }
        if (gps != null) {
            ex.setAttribute(ExifInterface.TAG_GPS_LATITUDE, dms(gps.first))
            ex.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (gps.first >= 0) "N" else "S")
            ex.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dms(gps.second))
            ex.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (gps.second >= 0) "E" else "W")
        } else if (clearGps) {
            for (t in listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF)) ex.setAttribute(t, null)
        }
        if (orientation.isNotBlank()) ex.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.trim())
        ex.saveAttributes()
        if (before > 0) file.setLastModified(before)
    }
}
