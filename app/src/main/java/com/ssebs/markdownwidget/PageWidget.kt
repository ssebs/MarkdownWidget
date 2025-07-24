package com.ssebs.markdownwidget;

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Html
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.TableLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.WorkManager
import com.ssebs.markdownwidget.R
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import androidx.core.net.toUri
import androidx.core.text.toHtml
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

object PageWidget: GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition
    fun Context.toPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics)

    val buttonSize = 45
    val paddingSize = 7

    val mdFilePathKey = stringPreferencesKey("mdFilePathKey")
    val vaultPathKey = stringPreferencesKey("vaultPathKey")
    val textKey = stringPreferencesKey("textKey")
    val showTools = booleanPreferencesKey("showTools")

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        super.onDelete(context, glanceId)
        val widgetCount = GlanceAppWidgetManager(context).getGlanceIds(PageWidget.javaClass).size
        if (widgetCount == 0)
        {
            //cancel once all the widgets are deleted
            WorkManager
                .getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val mdFilePath = currentState(key=mdFilePathKey) ?: ""
            val text = currentState(key=textKey) ?: getNoteText(context, mdFilePath)
            val vaultPath = currentState(key= vaultPathKey) ?: ""
//            val showTools = currentState(key=showTools) ?: true

//            val dpWidth = if (showTools)
//                LocalSize.current.width.value - buttonSize - paddingSize*2
//            else
//                LocalSize.current.width.value - paddingSize*2
//            val localWidth = context.toPx(dpWidth)

//            var encodedFile = mdFilePath.split("/").lastOrNull()?.dropLast(3)
//            encodedFile = URLEncoder.encode(encodedFile, "UTF-8").replace("+", "%20")
//            var encodedVault = vaultPath.split("/").lastOrNull()
//            encodedVault = URLEncoder.encode(encodedVault, "UTF-8").replace("+", "%20")
            val uri = mdFilePath.toUri()

            val openNote = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, "text/markdown")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                setPackage("net.gsantner.markor") // Force Markor as the target app
            }
//            val newNote = Intent(Intent.ACTION_VIEW,
//                Uri.parse("obsidian://new?vault=$encodedVault&name=New%20note")
//            )
//            val searchNote = Intent(
//                Intent.ACTION_VIEW,
//                Uri.parse("obsidian://search?vault=$encodedVault")
//            )
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(10.dp)
                    .padding(paddingSize.dp)
                    .background(Color(0xff262626)),
            ) {
                LazyColumn(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xff1e1e1e))
                        .cornerRadius(10.dp)
                ) {
                    item {
                        val remoteView = RemoteViews(LocalContext.current.packageName, R.layout.md_layout)
                        val flavour = CommonMarkFlavourDescriptor()
                        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(text)
                        val htmlString = HtmlGenerator(text, parsedTree, flavour, true).generateHtml()

                        remoteView.setCharSequence(
                            R.id.textView,
                            "setText",
                            Html.fromHtml(htmlString, Html.FROM_HTML_MODE_COMPACT)
                        )
                        AndroidRemoteViews(
                            remoteView,
                            modifier = GlanceModifier
                                .clickable(actionStartActivity(openNote))
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
    fun getNoteText(context: Context, uriString: String): String {
        if (uriString.isEmpty()) return "<empty>"
        return try {
            val uri = uriString.toUri()
            context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() } ?: "failed to load"
        } catch (e: Exception) {
            "failed to load"
        }
    }
}

class SimplePageWidgetReceiver: GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = PageWidget
}
