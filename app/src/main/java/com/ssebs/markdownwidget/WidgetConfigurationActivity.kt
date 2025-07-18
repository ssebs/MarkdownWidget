package com.ssebs.markdownwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest.Companion.MIN_PERIODIC_INTERVAL_MILLIS
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ssebs.markdownwidget.ui.theme.ObsidianAndroidWidgetsTheme
import com.ssebs.markdownwidget.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri


class WidgetConfigurationActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        WebView.enableSlowWholeDocumentDraw()
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                val uri = "package:${BuildConfig.APPLICATION_ID}".toUri()
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                )
            }
        }

        super.onCreate(savedInstanceState)
        var fileMutable = MutableStateFlow("")

        val getMarkdownFile =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK && it.data?.data != null)
                {
                    val uri = it.data!!.data!!
                    val path = uri.path?.split(":")?.getOrNull(1) ?: ""
                    fileMutable.value = path

                    // Extract vault as the parent directory of the file
                    val folder = File("/storage/emulated/0/$path").parent ?: ""

                    Log.d("file_path", fileMutable.value)
                    Log.d("test", fileMutable.value)
                }
            }

        setContent {
            val context = LocalContext.current

            val filePath by fileMutable.collectAsState()
            ObsidianAndroidWidgetsTheme {
                val appWidgetId = intent?.extras?.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Row {
                        Text("newline\n\n")
                    }
                    FilePicker(filePath = filePath, "Select Markdown File") {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/markdown"
                        }
                        getMarkdownFile.launch(intent)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = {
                            if (fileMutable.value == "")
                            {
                                Toast.makeText(baseContext, "Please Select a page to show!", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            CoroutineScope(Dispatchers.IO).launch {
                                val manager = GlanceAppWidgetManager(baseContext)
                                val gId = manager.getGlanceIdBy(appWidgetId)

                                updateAppWidgetState(context, gId) {
                                    it[PageWidget.mdFilePathKey] = fileMutable.value
                                    it[PageWidget.showTools] = true
                                }
                                PageWidget.update(context, gId)
                            }

                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                            startWorkManager(context)
                            finish()
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    private fun startWorkManager(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWidgetWorker>(
            MIN_PERIODIC_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
        ).build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

@Composable
fun FilePicker(filePath: String, buttonText: String, onClick: () -> Unit) {
    val scroll = rememberScrollState(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    )
    {
        Text(filePath, modifier = Modifier
            .width(250.dp)
            .horizontalScroll(scroll))
        Button(onClick = onClick, modifier = Modifier.padding(5.dp)) {
            Text(text = buttonText)
        }
    }
}

@Composable
fun CheckboxAndText(checked: Boolean, text: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)

        Text(text = text, modifier = Modifier.clickable { onCheckedChange(!checked) })
    }
}
