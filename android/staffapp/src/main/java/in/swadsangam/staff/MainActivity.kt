package `in`.swadsangam.staff

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Swad Sangam Admin / Staff app. Shows the restaurant's admin web app (same login, roles and data)
 * full-screen, and adds what a browser tab cannot: native printing of bills and KOTs, saving reports
 * to Downloads, photo upload from the camera roll, and phone / WhatsApp / UPI / maps links.
 * Only the restaurant's own site is loaded inside the app; every other link opens outside it.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var offline: View
    private lateinit var loading: View
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    /** Kept alive until Android has printed it (a local WebView could be garbage-collected mid-print); destroyed on the next print / exit. */
    private var printer: WebView? = null
    /** onPageFinished also runs after a failed load — this keeps the offline screen up until a load really succeeds. */
    private var mainFrameFailed = false
    private var cameraUri: Uri? = null
    private var notifId = 1000

    /** File chooser: any accepted type (images AND PDFs), several files when allowed, plus "Take photo". */
    private val chooseFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        val uris: Array<Uri>? = when {
            res.resultCode != RESULT_OK -> null
            data?.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
            data?.data != null -> arrayOf(data.data!!)
            cameraUri != null -> arrayOf(cameraUri!!) // camera wrote to our file; no data comes back
            else -> null
        }
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
        cameraUri = null
    }

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val siteBase: Uri = Uri.parse(BuildConfig.SITE_BASE)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.KEEP_SCREEN_ON) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        createAlertChannel()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        web = WebView(this).apply {
            setBackgroundColor(getColor(R.color.staff_bg))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true          // login session + preferences
            settings.mediaPlaybackRequiresUserGesture = false // new-order alert sound
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = settings.userAgentString + " SwadSangamApp/" + BuildConfig.VERSION_NAME
            addJavascriptInterface(AppBridge(), "SwadSangamApp")
            webViewClient = Client()
            webChromeClient = Chrome()
        }
        CookieManager.getInstance().setAcceptCookie(true)

        offline = offlineView()
        loading = loadingView()
        val content = FrameLayout(this)
        content.addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(loading, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(offline, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(edgeToEdgeShell(content))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(BuildConfig.SITE_BASE + BuildConfig.START_PATH)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); CookieManager.getInstance().flush(); super.onPause() }
    override fun onDestroy() { printer?.destroy(); printer = null; web.destroy(); super.onDestroy() }

    private fun isOwnSite(uri: Uri) = uri.scheme == "https" && uri.host == siteBase.host && (uri.path ?: "").startsWith(siteBase.path ?: "/")

    private fun openOutside(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (isOwnSite(uri)) return false
            // Supabase auth redirects and API calls are fetched in the page, never navigated; anything else leaves the app
            openOutside(uri)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            mainFrameFailed = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            loading.visibility = View.GONE
            if (!mainFrameFailed) offline.visibility = View.GONE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                mainFrameFailed = true
                offline.visibility = View.VISIBLE
            }
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            // accept="image/*,application/pdf" arrives as one or more strings; split and keep every type
            val types = params.acceptTypes.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val pick = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).apply {
                type = if (types.size == 1) types[0] else "*/*"
                if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            val chooser = Intent.createChooser(pick, getString(R.string.choose_file))
            if (types.isEmpty() || types.any { it.startsWith("image/") || it == "*/*" }) {
                cameraIntent()?.let { chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it)) }
            }
            return try { chooseFiles.launch(chooser); true } catch (e: ActivityNotFoundException) { fileCallback = null; false }
        }
    }

    /** Called from the web app (lib/native.ts). Runs on a background thread, so UI work is posted to the main thread. */
    inner class AppBridge {
        private val main = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun printHtml(title: String, html: String) = main.post {
            // Render the bill / KOT / payslip in an off-screen WebView, then hand it to Android printing
            val p = WebView(this@MainActivity)
            printer?.destroy()
            printer = p
            p.settings.javaScriptEnabled = false
            p.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) = print(title, view)
            }
            p.loadDataWithBaseURL(BuildConfig.SITE_BASE, html, "text/html", "utf-8", null)
        }

        @JavascriptInterface
        fun printPage(title: String) = main.post { print(title, web) }

        @JavascriptInterface
        fun saveText(filename: String, mime: String, text: String) = main.post {
            if (android.os.Build.VERSION.SDK_INT < 29) {
                // Android 8–9: no shared Downloads API without storage permission → share the report instead
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, filename).putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, filename))
                return@post
            }
            val ok = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SwadSangam")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert")
                contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(this@MainActivity, if (ok) getString(R.string.saved_to_downloads, filename) else "Could not save $filename", Toast.LENGTH_LONG).show()
        }

        @JavascriptInterface
        fun openExternal(url: String) = main.post { openOutside(Uri.parse(url)) }

        /** New online order / new delivery: a top-bar notification with sound (a WebView has no Notification API). */
        /** The web app reports its theme so the system-bar strips match its header and bottom bar. */
        @JavascriptInterface
        fun setChrome(theme: String) = main.post { applyChrome(dark = theme != "light") }

        @JavascriptInterface
        fun notify(title: String, body: String) = main.post { showAlert(title.take(80), body.take(400)) }
    }

    private fun print(title: String, view: WebView) {
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        pm.print("Swad Sangam – $title", view.createPrintDocumentAdapter(title), PrintAttributes.Builder().build())
    }

    /** Camera capture into our cache, shared through FileProvider (no storage permission needed). */
    private fun cameraIntent(): Intent? = runCatching {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        cameraUri = uri
        Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .takeIf { it.resolveActivity(packageManager) != null }
    }.getOrNull()

    private fun createAlertChannel() {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ch = NotificationChannel(ALERT_CHANNEL, getString(R.string.notif_channel_orders), NotificationManager.IMPORTANCE_HIGH).apply {
            description = getString(R.string.notif_channel_orders_desc)
            enableVibration(true)
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun showAlert(title: String, body: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(getColor(R.color.staff_brand))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(this).notify(notifId++, n) } catch (_: SecurityException) { }
    }

    /** Branded screen while the first page loads on slow data (instead of a blank dark screen). */
    private fun loadingView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(getColor(R.color.staff_bg))
        addView(ImageView(context).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(dp(88), dp(88)))
        addView(ProgressBar(context).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { topMargin = dp(20) })
        addView(TextView(context).apply {
            setText(R.string.loading); textSize = 15f; setTextColor(getColor(R.color.staff_on_bg_muted)); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }

    companion object { private const val ALERT_CHANNEL = "staff_alerts" }

    // ---------------------------------------------------------------------------------------------
    // System bars. targetSdk 35+ draws every app edge-to-edge on Android 15+, so without this the web
    // header slid under the clock and status icons. The page sits between two native strips sized by the
    // real insets (status bar / cutout on top, navigation bar or keyboard at the bottom), painted in the
    // web theme's colours so the bars look like part of the app.
    // ---------------------------------------------------------------------------------------------
    private lateinit var statusStrip: View
    private lateinit var navStrip: View
    private var darkChrome = true

    private fun edgeToEdgeShell(content: View): View {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        statusStrip = View(this)
        navStrip = View(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(navStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            statusStrip.layoutParams = statusStrip.layoutParams.apply { height = bars.top }
            // keyboard open: the page shrinks above it, so focused inputs are never hidden
            navStrip.layoutParams = navStrip.layoutParams.apply { height = maxOf(bars.bottom, ime.bottom) }
            v.setPadding(bars.left, 0, bars.right, 0)
            statusStrip.requestLayout(); navStrip.requestLayout()
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        applyChrome(dark = true)
        return root
    }

    /** Status/navigation strip colours + icon contrast; called with the web app's theme (light/dark). */
    private fun applyChrome(dark: Boolean) {
        darkChrome = dark
        statusStrip.setBackgroundColor(getColor(if (dark) R.color.chrome_dark_top else R.color.chrome_light_top))
        navStrip.setBackgroundColor(getColor(if (dark) R.color.chrome_dark_bottom else R.color.chrome_light_bottom))
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun offlineView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(getColor(R.color.staff_bg))
        val pad = dp(24); setPadding(pad, pad, pad, pad)
        visibility = View.GONE
        addView(TextView(context).apply {
            setText(R.string.offline_title); textSize = 22f; setTextColor(getColor(R.color.staff_on_bg)); gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            setText(R.string.offline_body); textSize = 15f; setTextColor(getColor(R.color.staff_on_bg_muted)); gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        })
        addView(Button(context).apply {
            setText(R.string.retry)
            minHeight = dp(48); minimumHeight = dp(48)
            setBackgroundColor(getColor(R.color.staff_brand)); setTextColor(getColor(R.color.staff_on_bg))
            setOnClickListener { this@MainActivity.mainFrameFailed = false; this@MainActivity.offline.visibility = View.GONE; web.reload() }
        })
    }
}
