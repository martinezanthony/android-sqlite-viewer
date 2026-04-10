package io.github.martinezanthony.sqliteviewer.ui.viewer

import io.github.martinezanthony.sqliteviewer.R

enum class ExportFormat(
    val extension: String,
    val separator: String,
    val mimeType: String,
    val labelRes: Int
) {
    CSV("csv",  ",",  "text/csv",            R.string.export_option_csv),
    TXT("txt",  "\t", "text/plain",          R.string.export_option_txt),
    JSON("json", "", "application/json",     R.string.export_option_json),
}