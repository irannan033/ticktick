package com.ppm.momentum.export

import android.content.Context
import com.ppm.momentum.data.CategoryEntity
import com.ppm.momentum.data.PomodoroEntity
import com.ppm.momentum.data.TaskEntity
import com.ppm.momentum.domain.computeStats
import com.ppm.momentum.domain.dailyCompletions
import com.ppm.momentum.domain.focusSeconds
import com.ppm.momentum.domain.referenceTime
import com.ppm.momentum.util.toDate
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    CSV("CSV", "csv", "text/csv"),
    XLSX("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("PDF", "pdf", "application/pdf"),
}

object Exporter {
    /** Writes the report into the app cache (shared through FileProvider). Call off the main thread. */
    fun create(
        ctx: Context, format: ExportFormat, rangeLabel: String, tasks: List<TaskEntity>, cats: List<CategoryEntity>,
        sessions: List<PomodoroEntity>, now: Long, range: LongRange?,
    ): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }

        val inRange = if (range == null) tasks else tasks.filter { it.referenceTime() in range }
        val sess = if (range == null) sessions else sessions.filter { it.startedAt in range }
        val rows = exportRows(inRange, cats, sess, now)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val file = File(dir, "momentum-report-$stamp.${format.ext}")

        FileOutputStream(file).use { out ->
            when (format) {
                ExportFormat.CSV -> writeCsv(out, listOf(EXPORT_HEADER) + rows)
                ExportFormat.XLSX -> writeXlsx(out, "Tasks", listOf(EXPORT_HEADER) + rows)
                ExportFormat.PDF -> PdfReport.write(
                    out, "Momentum productivity report", rangeLabel,
                    computeStats(inRange, now, null), focusSeconds(sess, null) / 60,
                    dailyCompletions(tasks, now.toDate(), 14), EXPORT_HEADER, rows,
                )
            }
        }
        return file
    }
}
