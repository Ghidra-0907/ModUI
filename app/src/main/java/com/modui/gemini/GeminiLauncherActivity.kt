package com.modui.gemini

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import coil.load
import coil.request.CachePolicy
import com.modui.gemini.databinding.ActivityGeminiLauncherBinding
import java.io.File

class GeminiLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeminiLauncherBinding
    private lateinit var prefManager: PrefManager

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null
    private var activeVideoPath: String? = null
    private var activeVideoKey: String? = null
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    private val modCssBase64: String by lazy {
        val rawCss = assets.open("mod_ui.css").bufferedReader().use { it.readText() }
        Base64.encodeToString(rawCss.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private val modScriptRaw: String by lazy {
        assets.open("mod_script.js").bufferedReader().use { it.readText() }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult

        val uris = extractFileChooserResult(result.resultCode, result.data)
        callback.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        binding = ActivityGeminiLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PrefManager(this)
        applySystemInsets()
        setupTextureViewListener()
        setupWebView()
        setupBackNavigation()

        applyVisualLayers()
        binding.webView.loadUrl(GEMINI_APP_URL)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyVisualLayers()
        syncModUiWithCurrentPage()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        applyVisualLayers()
        syncModUiWithCurrentPage()
        resumeVideoIfReady()
    }

    override fun onPause() {
        pauseVideoIfPlaying()
        binding.webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        releaseVideoPlayer()
        binding.webView.apply {
            stopLoading()
            webChromeClient = null
            destroy()
        }
        super.onDestroy()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.launcherRoot) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(sysBars.bottom, ime.bottom)

            // Background ImageView / TextureView / DimOverlay stay 100% edge-to-edge;
            // only the WebView container respects system bars & keyboard IME.
            binding.webViewContainer.updatePadding(
                left = sysBars.left,
                top = sysBars.top,
                right = sysBars.right,
                bottom = bottomInset
            )
            binding.progressBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = sysBars.top
            }
            insets
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun applyVisualLayers() {
        val modEnabled = prefManager.isModEnabled

        if (!modEnabled) {
            releaseVideoPlayer()
            binding.ivBackground.visibility = View.GONE
            binding.tvVideoBackground.visibility = View.GONE
            binding.viewDimOverlay.visibility = View.GONE
            binding.webView.setBackgroundColor(
                ContextCompat.getColor(this, R.color.gemini_default_dark)
            )
            binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            return
        }

        // Mod UI ON: Make WebView transparent so background + dim overlay shine through
        binding.webView.setBackgroundColor(Color.TRANSPARENT)
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Middle Layer: Overlay Dim (0% .. 80%)
        binding.viewDimOverlay.visibility = View.VISIBLE
        binding.viewDimOverlay.alpha = prefManager.overlayDimPercent / 100f

        // Bottom Layer: STATIC / GIF (ImageView + Coil) or VIDEO (TextureView + MediaPlayer)
        when (prefManager.bgType) {
            BgType.VIDEO -> {
                val videoFile = prefManager.getAnimatedVideoFile()
                if (prefManager.bgSource == BgSource.CUSTOM && videoFile.exists() && videoFile.length() > 0L) {
                    binding.ivBackground.visibility = View.GONE
                    binding.tvVideoBackground.visibility = View.VISIBLE
                    startOrKeepVideo(videoFile)
                } else {
                    showFallbackStaticOrPreset()
                }
            }

            BgType.GIF -> {
                releaseVideoPlayer()
                binding.tvVideoBackground.visibility = View.GONE
                binding.ivBackground.visibility = View.VISIBLE

                val customGif = prefManager.getAnimatedGifFile()
                if (prefManager.bgSource == BgSource.CUSTOM && customGif.exists() && customGif.length() > 0L) {
                    loadCustomImageWithCoil(customGif)
                } else {
                    val preset = prefManager.getSelectedPreset()
                        ?.takeIf { it.type == BgType.GIF }
                        ?: PrefManager.ANIMATED_PRESETS.first()
                    binding.ivBackground.load(preset.assetUri) {
                        crossfade(false)
                    }
                }
            }

            BgType.STATIC -> {
                releaseVideoPlayer()
                binding.tvVideoBackground.visibility = View.GONE
                binding.ivBackground.visibility = View.VISIBLE

                val customStatic = prefManager.getStaticBgFile()
                if (prefManager.bgSource == BgSource.CUSTOM && customStatic.exists() && customStatic.length() > 0L) {
                    loadCustomImageWithCoil(customStatic)
                } else {
                    showFallbackStaticOrPreset()
                }
            }
        }
    }

    private fun showFallbackStaticOrPreset() {
        releaseVideoPlayer()
        binding.tvVideoBackground.visibility = View.GONE
        binding.ivBackground.visibility = View.VISIBLE
        val preset = prefManager.getSelectedPreset()
            ?.takeIf { it.type == BgType.STATIC }
            ?: PrefManager.STATIC_PRESETS.first()
        binding.ivBackground.load(preset.assetUri) {
            crossfade(false)
        }
    }

    private fun loadCustomImageWithCoil(file: File) {
        val version = prefManager.customMediaVersion
        binding.ivBackground.load(file) {
            crossfade(false)
            memoryCacheKey("${file.absolutePath}_$version")
            diskCachePolicy(CachePolicy.DISABLED)
        }
    }

    // -------------------------------------------------------------------------
    // 60fps Video Background Layer (TextureView + MediaPlayer, center-cropped)
    // -------------------------------------------------------------------------

    private fun setupTextureViewListener() {
        binding.tvVideoBackground.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                videoSurface?.release()
                videoSurface = Surface(surface)
                val path = activeVideoPath
                if (path != null) {
                    prepareMediaPlayer(path)
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                updateVideoCenterCropTransform()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseVideoPlayer()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    private fun startOrKeepVideo(videoFile: File) {
        val path = videoFile.absolutePath
        val key = "$path:${prefManager.customMediaVersion}"
        if (activeVideoKey == key && mediaPlayer != null) {
            resumeVideoIfReady()
            return
        }
        activeVideoPath = path
        activeVideoKey = key
        val st = binding.tvVideoBackground.surfaceTexture
        if (binding.tvVideoBackground.isAvailable && st != null) {
            if (videoSurface == null) {
                videoSurface = Surface(st)
            }
            prepareMediaPlayer(path)
        }
    }

    private fun prepareMediaPlayer(videoPath: String) {
        releaseMediaPlayerOnly()
        val surface = videoSurface ?: return

        try {
            val mp = MediaPlayer().apply {
                setDataSource(videoPath)
                setSurface(surface)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, width, height ->
                    this@GeminiLauncherActivity.videoWidth = width
                    this@GeminiLauncherActivity.videoHeight = height
                    updateVideoCenterCropTransform()
                }
                setOnPreparedListener { player ->
                    updateVideoCenterCropTransform()
                    player.start()
                }
                setOnErrorListener { _, _, _ ->
                    showFallbackStaticOrPreset()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (_: Exception) {
            showFallbackStaticOrPreset()
        }
    }

    private fun updateVideoCenterCropTransform() {
        val viewWidth = binding.tvVideoBackground.width.toFloat()
        val viewHeight = binding.tvVideoBackground.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return

        val scaleX = viewWidth / videoWidth.toFloat()
        val scaleY = viewHeight / videoHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)

        val scaledWidth = videoWidth * scale
        val scaledHeight = videoHeight * scale
        val dx = (viewWidth - scaledWidth) / 2f
        val dy = (viewHeight - scaledHeight) / 2f

        val matrix = Matrix().apply {
            setScale(scaledWidth / viewWidth, scaledHeight / viewHeight)
            postTranslate(dx, dy)
        }
        binding.tvVideoBackground.setTransform(matrix)
    }

    private fun pauseVideoIfPlaying() {
        runCatching {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        }
    }

    private fun resumeVideoIfReady() {
        runCatching {
            if (prefManager.isModEnabled && prefManager.bgType == BgType.VIDEO) {
                val mp = mediaPlayer
                if (mp != null && !mp.isPlaying) {
                    mp.start()
                }
            }
        }
    }

    private fun releaseMediaPlayerOnly() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    private fun releaseVideoPlayer() {
        releaseMediaPlayerOnly()
        videoSurface?.release()
        videoSurface = null
        activeVideoPath = null
        activeVideoKey = null
        videoWidth = 0
        videoHeight = 0
    }

    // -------------------------------------------------------------------------
    // Top Layer: Transparent Hardware-Accelerated WebView for Google Gemini
    // -------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)

            // Remove "; wv" (and "Version/x.x ") so Google Sign-In treats this as Chrome Android
            val originalUa = userAgentString ?: ""
            userAgentString = originalUa
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val host = uri.host?.lowercase() ?: return false
                if (isAllowedInAppHost(host)) {
                    return false
                }
                // Open external citations / links in the system browser
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                return true
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                injectOrClearModUi(view, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectOrClearModUi(view, url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                injectOrClearModUi(view, url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.setProgressCompat(newProgress, true)
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@GeminiLauncherActivity.filePathCallback?.onReceiveValue(null)
                this@GeminiLauncherActivity.filePathCallback = filePathCallback

                val allowMultiple =
                    fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                val acceptTypes = fileChooserParams?.acceptTypes
                    ?.filter { it.isNotBlank() }
                    ?.toTypedArray()

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    if (!acceptTypes.isNullOrEmpty()) {
                        putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                    }
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                }

                return try {
                    fileChooserLauncher.launch(Intent.createChooser(intent, "Đính kèm tệp vào Gemini"))
                    true
                } catch (_: Exception) {
                    this@GeminiLauncherActivity.filePathCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                val originHost = request.origin?.host?.lowercase()
                if (originHost != null && originHost.endsWith("gemini.google.com")) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                }
            }
        }
    }

    private fun extractFileChooserResult(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != RESULT_OK || data == null) return null

        val clipData = data.clipData
        if (clipData != null && clipData.itemCount > 0) {
            val uris = mutableListOf<Uri>()
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uris.add(it) }
            }
            if (uris.isNotEmpty()) return uris.toTypedArray()
        }

        return WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            ?: data.data?.let { arrayOf(it) }
    }

    private fun isAllowedInAppHost(host: String): Boolean {
        return host == "gemini.google.com" ||
            host.endsWith(".gemini.google.com") ||
            host == "accounts.google.com" ||
            host == "accounts.youtube.com" ||
            host == "myaccount.google.com" ||
            host == "consent.google.com" ||
            host == "gds.google.com" ||
            host == "ogs.google.com" ||
            host == "signin.google.com" ||
            host.endsWith(".googleusercontent.com")
    }

    private fun isGeminiHost(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "gemini.google.com" || host.endsWith(".gemini.google.com")
    }

    private fun syncModUiWithCurrentPage() {
        val currentUrl = binding.webView.url
        if (!currentUrl.isNullOrBlank()) {
            injectOrClearModUi(binding.webView, currentUrl)
        }
    }

    private fun injectOrClearModUi(webView: WebView?, url: String?) {
        webView ?: return
        if (!isGeminiHost(url)) return

        if (!prefManager.isModEnabled) {
            webView.evaluateJavascript(
                "if (window.__MOD_UI_REMOVE__) { window.__MOD_UI_REMOVE__(); }",
                null
            )
            return
        }

        val blurPx = prefManager.glassBlurPx
        val ambientGlow = prefManager.isAmbientGlowEnabled
        val hideSuggestions = prefManager.isHideSuggestionsEnabled

        val injectionJs = """
            (function() {
              var styleId = 'mod-ui-liquid-glass-style';
              var b64 = '$modCssBase64';
              var cssText;
              try {
                var bin = atob(b64);
                var bytes = new Uint8Array(bin.length);
                for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                cssText = new TextDecoder('utf-8').decode(bytes);
              } catch (e) {
                cssText = atob(b64);
              }
              var styleEl = document.getElementById(styleId);
              if (!styleEl) {
                styleEl = document.createElement('style');
                styleEl.id = styleId;
                (document.head || document.documentElement).appendChild(styleEl);
              }
              if (styleEl.textContent !== cssText) {
                styleEl.textContent = cssText;
              }
              window.__MOD_UI_CONFIG__ = {
                blurPx: $blurPx,
                ambientGlow: $ambientGlow,
                hideSuggestions: $hideSuggestions
              };
            })();
            $modScriptRaw
            if (window.__MOD_UI_APPLY__) {
              window.__MOD_UI_APPLY__(window.__MOD_UI_CONFIG__);
            }
        """.trimIndent()

        webView.evaluateJavascript(injectionJs, null)
    }

    companion object {
        private const val GEMINI_APP_URL = "https://gemini.google.com/app"
    }
}
