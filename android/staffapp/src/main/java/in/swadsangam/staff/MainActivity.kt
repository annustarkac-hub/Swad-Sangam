package `in`.swadsangam.staff

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Swad Sangam Admin / Staff app. Shows the restaurant's admin web app (same login, roles and data)
 * full-screen, and adds what a browser tab cannot: native printing of bills and KOTs, saving reports
 * to Downloads, photo upload from the camera roll, and phone / WhatsApp / UPI / maps links.
 * Only the restaurant's own site is loaded inside the app; every other link opens outside it.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var offline: View
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    /** Kept alive until Android has printed it (a local WebView could be garbage-collected mid-print). */
    private var printer: WebView? = null

    private val pickFiles = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    private val siteBase: Uri = Uri.parse(BuildConfig.SITE_BASE)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.KEEP_SCREEN_ON) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
        val root = FrameLayout(this)
        root.addView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(offline, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

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
    override fun onDestroy() { web.destroy(); super.onDestroy() }

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

        override fun onPageFinished(view: WebView, url: String?) {
            offline.visibility = View.GONE
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) offline.visibility = View.VISIBLE
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            val type = params.acceptTypes.firstOrNull { it.isNotBlank() } ?: "*/*"
            return try { pickFiles.launch(type); true } catch (e: ActivityNotFoundException) { fileCallback = null; false }
        }
    }

    /** Called from the web app (lib/native.ts). Runs on a background thread, so UI work is posted to the main thread. */
    inner class AppBridge {
        private val main = Handler(Looper.getMainLooper())

        @JavascriptInterface
        fun printHtml(title: String, html: String) = main.post {
            // Render the bill / KOT / payslip in an off-screen WebView, then hand it to Android printing
            val p = WebView(this@MainActivity)
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
    }

    private fun print(title: String, view: WebView) {
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        pm.print("Swad Sangam – $title", view.createPrintDocumentAdapter(title), PrintAttributes.Builder().build())
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
            setOnClickListener { this@MainActivity.offline.visibility = View.GONE; web.reload() }
        })
    }
}
