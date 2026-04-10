package io.github.martinezanthony.sqliteviewer.ui.viewer

import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.martinezanthony.sqliteviewer.R
import io.github.martinezanthony.sqliteviewer.databinding.ActivityDatabaseViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * displays the contents of a SQLite database file
 *
 * Allows the user to select a table, apply per-column LIKE filters with an optional
 * row limit, view paginated results in a horizontally scrollable table, and export results
 */

class DatabaseViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatabaseViewerBinding

    private var currentDbPath: String? = null
    private var currentTable: String? = null

    private val inputFieldsMap = mutableMapOf<String, EditText>()

    /** cached results from the last successful query, used for export */
    private var lastColumns: List<String>? = null
    private var lastRows: List<List<String?>>? = null

    private var pendingExportFormat: ExportFormat? = null
    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val format = pendingExportFormat ?: return@registerForActivityResult
            uri ?: return@registerForActivityResult
            writeExportToUri(uri, format)
            pendingExportFormat = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDatabaseViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.btnExportResults.setOnClickListener { showExportDialog() }

        // Rebuild filter fields whenever the selected table changes
        binding.spinnerTables.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTable = parent?.getItemAtPosition(position).toString()
                buildFilterFields(currentTable!!)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnSearch.setOnClickListener { onSearchClicked() }

        intent.getStringExtra(EXTRA_DB_PATH)?.let { path ->
            currentDbPath = path
            binding.tvDbName.text = getString(R.string.viewer_db_loaded, File(path).name)
            loadTables()
        }
    }

    /**
     * Hides the filter/search UI in landscape orientation to maximise the results table area.
     * Restores visibility in portrait
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val visibility = if (isLandscape) View.GONE else View.VISIBLE

        listOf(
            binding.tvDbName, binding.selectATable, binding.spinnerTables,
            binding.divider1, binding.dynamicSearchFields, binding.btnSearch, binding.divider2
        ).forEach { it.visibility = visibility }

        (binding.containerInputs.parent as? View)?.visibility = visibility
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) currentDbPath?.let { File(it).delete() }
    }

    private fun setupEdgeToEdge() {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val origLeft = binding.main.paddingLeft
        val origRight = binding.main.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = origLeft + bars.left, top = bars.top,
                right = origRight + bars.right, bottom = bars.bottom
            )
            insets
        }
    }

    // configure the spinner with the tables found. If no tables are found or an error occurs while opening the database, show a toast and finish the cctivity
    private fun loadTables() {
        try {
            val db = SQLiteDatabase.openDatabase(currentDbPath!!, null, SQLiteDatabase.OPEN_READONLY)
            val tables = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'" +
                        " AND name NOT LIKE 'android%' AND name NOT LIKE 'sqlite%'",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) tables.add(cursor.getString(0))
            }
            db.close()

            if (tables.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_tables_found, Toast.LENGTH_SHORT).show()
                return
            }

            binding.spinnerTables.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tables)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.toast_not_sqlite_db, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // dynamically rebuild the filter fields for the selected table
    private fun buildFilterFields(tableName: String) {
        binding.containerInputs.removeAllViews()
        inputFieldsMap.clear()

        val db = SQLiteDatabase.openDatabase(currentDbPath!!, null, SQLiteDatabase.OPEN_READONLY)
        db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")

            addLimitField()

            while (cursor.moveToNext()) {
                val colName = cursor.getString(nameIndex)
                val label = buildLabel(getString(R.string.viewer_field_label, colName))
                val editText = EditText(this).apply {
                    hint = getString(R.string.viewer_field_hint, colName)
                    setSingleLine()
                }
                binding.containerInputs.addView(label)
                binding.containerInputs.addView(editText)
                inputFieldsMap[colName] = editText
            }
        }
        db.close()
    }

    // inserts the numeric LIMIT input field at the top of the filter container
    private fun addLimitField() {
        binding.containerInputs.addView(buildLabel(getString(R.string.viewer_limit_label)))
        binding.containerInputs.addView(EditText(this).apply {
            hint = getString(R.string.viewer_limit_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            tag = TAG_LIMIT
        })
    }

    private fun buildLabel(text: String) = TextView(this).apply {
        this.text = text
        setPadding(0, 10, 0, 0)
    }

    /**
     * Validates that a database and table are loaded and that at least one filter
     * or limit value is provided before delegating to executeSearch
     */
    private fun onSearchClicked() {
        if (currentDbPath == null) {
            Toast.makeText(this, R.string.toast_load_db_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (currentTable == null) {
            Toast.makeText(this, R.string.toast_select_table_first, Toast.LENGTH_SHORT).show()
            return
        }

        val limitField = binding.containerInputs.findViewWithTag<EditText>(TAG_LIMIT)
        val hasInput = inputFieldsMap.values.any { it.text.isNotBlank() }
                || limitField?.text?.isNotBlank() == true

        if (!hasInput) {
            Toast.makeText(this, R.string.toast_enter_at_least_one_value, Toast.LENGTH_SHORT).show()
            return
        }

        executeSearch(limitField)
    }

    // runs the query on Dispatchers.IO and renders results on the main thread
    private fun executeSearch(limitField: EditText?) {
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val (query, args) = buildQuery(limitField) ?: return@launch

                val (columns, rows) = withContext(Dispatchers.IO) {
                    val db = SQLiteDatabase.openDatabase(
                        currentDbPath!!, null, SQLiteDatabase.OPEN_READONLY
                    )
                    db.rawQuery(query, args.toTypedArray()).use { cursor ->
                        val cols = cursor.columnNames.toList()
                        val rowList = mutableListOf<List<String?>>()
                        while (cursor.moveToNext()) {
                            rowList.add(List(cursor.columnCount) { cursor.getString(it) })
                        }
                        cols to rowList
                    }.also { db.close() }
                }

                lastColumns = columns
                lastRows = rows

                renderResults(columns, rows)

                if (rows.isEmpty()) {
                    Toast.makeText(this@DatabaseViewerActivity, R.string.toast_no_matches, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@DatabaseViewerActivity,
                    getString(R.string.toast_search_error, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            } finally {
                binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    // Builds a `SELECT` query from the active filter inputs
    private fun buildQuery(limitField: EditText?): Pair<String, List<String>>? {
        val sb = StringBuilder("SELECT * FROM $currentTable WHERE 1=1")
        val args = mutableListOf<String>()

        inputFieldsMap.forEach { (col, editText) ->
            val value = editText.text.toString().trim()
            if (value.isNotEmpty()) {
                sb.append(" AND $col LIKE ?")
                args.add("%$value%")
            }
        }

        val limitStr = limitField?.text?.toString()?.trim()
        if (!limitStr.isNullOrEmpty()) {
            val limit = limitStr.toIntOrNull()
            if (limit == null || limit <= 0) {
                Toast.makeText(this, R.string.toast_invalid_limit, Toast.LENGTH_SHORT).show()
                return null
            }
            sb.append(" LIMIT ?")
            args.add(limit.toString())
        }

        return sb.toString() to args
    }

    // Render the query results in the horizontal table
    private fun renderResults(columns: List<String>, rows: List<List<String?>>) {
        val densityDp = resources.displayMetrics.density
        val cellPaddingPx = (CELL_PADDING_DP * densityDp).roundToInt()

        val measureTv = TextView(this)
        val normalPaint = measureTv.paint
        val boldPaint = android.graphics.Paint(normalPaint).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        val columnWidths = IntArray(columns.size) { colIndex ->
            var maxPx = boldPaint.measureText(columns[colIndex])
            rows.forEach { row ->
                val w = normalPaint.measureText(row.getOrNull(colIndex) ?: "")
                if (w > maxPx) maxPx = w
            }
            (maxPx + cellPaddingPx * 2).roundToInt().coerceAtMost(MAX_CELL_WIDTH_PX)
        }

        buildHeader(columns, columnWidths, cellPaddingPx)

        binding.rvResults.adapter = TableResultsAdapter(rows, columnWidths, cellPaddingPx, MAX_CELL_WIDTH_PX)
        binding.tvResultsInfo.text = getString(R.string.viewer_results_count, rows.size)
    }

    private fun buildHeader(columns: List<String>, columnWidths: IntArray, cellPaddingPx: Int) {
        binding.headerContainer.removeAllViews()
        columns.forEachIndexed { i, colName ->
            val tv = TextView(this).apply {
                text = colName
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(getColor(R.color.table_header_text))
                setPadding(cellPaddingPx, cellPaddingPx, cellPaddingPx, cellPaddingPx)
                layoutParams = LinearLayout.LayoutParams(
                    columnWidths[i], LinearLayout.LayoutParams.WRAP_CONTENT
                )
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            binding.headerContainer.addView(tv)
        }
    }

    /** Shows a dialog that lets the user choose between the export formats */
    private fun showExportDialog() {
        val formats = ExportFormat.entries.toTypedArray()
        val labels = formats.map { getString(it.labelRes) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.export_dialog_title)
            .setItems(labels) { _, which ->
                val format = formats[which]
                pendingExportFormat = format
                createDocumentLauncher.launch(
                    "export_${currentTable}_${System.currentTimeMillis()}.${format.extension}"
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * writes the last query results to the user-selected document URI using the chosen export format.
     * runs the file operation on Dispatchers.IO, shows success/error toasts, and displays a loading overlay
     */
    private fun writeExportToUri(uri: android.net.Uri, format: ExportFormat) {
        val columns = lastColumns
        val rows = lastRows

        if (columns == null || rows.isNullOrEmpty()) {
            Toast.makeText(this, R.string.toast_no_data_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)!!.bufferedWriter().use { out ->
                    when (format) {
                        ExportFormat.JSON -> {
                            val jsonArray = org.json.JSONArray()
                            rows.forEach { row ->
                                val jsonObject = org.json.JSONObject()
                                columns.forEachIndexed { colIndex, col ->
                                    val raw = row.getOrNull(colIndex)
                                    when {
                                        raw == null                  -> jsonObject.put(col, org.json.JSONObject.NULL)
                                        raw.toIntOrNull() != null    -> jsonObject.put(col, raw.toInt())
                                        raw.toLongOrNull() != null   -> jsonObject.put(col, raw.toLong())
                                        raw.toDoubleOrNull() != null -> jsonObject.put(col, raw.toDouble())
                                        else                         -> jsonObject.put(col, raw)
                                    }
                                }
                                jsonArray.put(jsonObject)
                            }
                            out.write(jsonArray.toString(2))
                        }
                        else -> {
                            out.write(columns.joinToString(format.separator))
                            out.newLine()
                            rows.forEach { row ->
                                out.write(row.joinToString(format.separator) { it ?: "" })
                                out.newLine()
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseViewerActivity,
                        getString(R.string.toast_export_success_ext, format.extension),
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseViewerActivity,
                        getString(R.string.toast_export_error, e.message ?: ""),
                        Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) { binding.loadingOverlay.visibility = View.GONE }
            }
        }
    }

    companion object {
        /** intent extra key for the local path of the SQLite database file to open */
        const val EXTRA_DB_PATH = "db_path"
        /** tag used to find the limit [EditText] inside the dynamic filter container */
        private const val TAG_LIMIT = "limit"
        private const val CELL_PADDING_DP = 8f
        private const val MAX_CELL_WIDTH_PX = 600
    }
}