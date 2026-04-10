package io.github.martinezanthony.sqliteviewer.ui.main

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.martinezanthony.sqliteviewer.R
import io.github.martinezanthony.sqliteviewer.data.RecentFilesRepository
import io.github.martinezanthony.sqliteviewer.databinding.ActivityMainBinding
import io.github.martinezanthony.sqliteviewer.ui.viewer.DatabaseViewerActivity
import io.github.martinezanthony.sqliteviewer.utils.DatabaseUtils
import io.github.martinezanthony.sqliteviewer.utils.FileUtils
import io.github.martinezanthony.sqliteviewer.utils.dpToPxInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles file selection via the system picker,
 * manages the recent files list
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecentFilesAdapter
    private lateinit var repository: RecentFilesRepository

    /** In-memory list of recently opened URIs (persisted via RecentFilesRepository). */
    private val recentUris = mutableListOf<String>()

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            grantPersistablePermission(uri)
            processAndOpen(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        repository = RecentFilesRepository(this)

        displayAppVersion()
        DatabaseUtils.clearCache(cacheDir)
        setupRecyclerView()
        loadRecentFiles()

        binding.btnLoadDb.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }
        binding.aboutContainer.setOnClickListener {
            showInfoDialog()
        }
    }

    private fun setupEdgeToEdge() {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun displayAppVersion() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }

        binding.tvAppVersion.text = getString(R.string.main_app_version, version)
    }

    // initializes the RecyclerView with RecentFilesAdapter and wires click/delete callbacks
    private fun setupRecyclerView() {
        adapter = RecentFilesAdapter(
            uris = recentUris.map { it.toUri() },
            onClick = ::processAndOpen,
            onDeleteClick = { removeRecentFile(it.toString()) }
        )
        binding.rvRecentFiles.layoutManager = LinearLayoutManager(this)
        binding.rvRecentFiles.adapter = adapter
    }

    // Loads persisted recent URIs and refreshes the adapter
    private fun loadRecentFiles() {
        recentUris.clear()
        recentUris.addAll(repository.load())
        adapter.updateData(recentUris.map { it.toUri() })
    }

    /**
     * removes the given URI from the recent list, persists the change,
     * and notifies the user via a toast
     */
    private fun removeRecentFile(uriString: String) {
        recentUris.remove(uriString)
        adapter.updateData(recentUris.map { it.toUri() })
        repository.save(recentUris)
        Toast.makeText(this, R.string.toast_recent_removed, Toast.LENGTH_SHORT).show()
    }

    // moves the given URI to the top of the recent list and persists the updated order
    private fun pushToRecents(uriString: String) {
        recentUris.removeAll { it == uriString }
        recentUris.add(0, uriString)
        adapter.updateData(recentUris.map { it.toUri() })
        repository.save(recentUris)
    }

    private fun grantPersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Validates, copies to cache, and opens the database file
    private fun processAndOpen(uri: Uri) {
        if (!DatabaseUtils.isSqliteDatabase(contentResolver, uri)) {
            Toast.makeText(this, R.string.toast_invalid_db_file, Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = FileUtils.getDisplayName(contentResolver, uri)
            ?: getString(R.string.recent_file_unknown)

        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val dbPath = withContext(Dispatchers.IO) {
                    DatabaseUtils.copyToCache(contentResolver, cacheDir, uri, fileName)
                }
                pushToRecents(uri.toString())
                startDatabaseViewer(dbPath)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    R.string.toast_file_access_error,
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            } finally {
                binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    // Starts DatabaseViewerActivity with the given local database file path
    private fun startDatabaseViewer(path: String) {
        val intent = Intent(this, DatabaseViewerActivity::class.java)
            .putExtra(DatabaseViewerActivity.EXTRA_DB_PATH, path)
        startActivity(intent)
    }

    private fun showInfoDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPxInt(24f), dpToPxInt(16f), dpToPxInt(24f), dpToPxInt(8f))
        }

        val licenseUrl = getString(R.string.url_license)
        val fullBody   = getString(R.string.info_dialog_body)
        val linkPhrase = getString(R.string.info_dialog_license_phrase)

        val spannable = SpannableString(fullBody)
        val startIndex = fullBody.indexOf(linkPhrase)
        if (startIndex >= 0) {
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(Intent(Intent.ACTION_VIEW, licenseUrl.toUri()))
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = ContextCompat.getColor(this@MainActivity, R.color.link_color)
                        ds.isUnderlineText = true
                    }
                },
                startIndex,
                startIndex + linkPhrase.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val bodyText = TextView(this).apply {
            text = spannable
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val linkText = TextView(this).apply {
            text = getString(R.string.info_dialog_source_code)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.link_color))
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpToPxInt(20f) }
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.url_source_code).toUri()))
            }
        }

        root.addView(bodyText)
        root.addView(linkText)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.info_dialog_title))
            .setView(root)
            .setPositiveButton(getString(R.string.info_dialog_ok)) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}