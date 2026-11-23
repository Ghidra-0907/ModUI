package com.modui.gemini

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.transition.TransitionManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.modui.gemini.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefManager: PrefManager
    private lateinit var backgroundAdapter: BackgroundAdapter

    private var currentTabIsAnimated: Boolean = false
    private var selectedBgType: BgType = BgType.STATIC
    private var selectedBgSource: BgSource = BgSource.PRESET
    private var selectedPresetId: String = PrefManager.STATIC_PRESETS.first().id

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            updateShizukuUi(animate = true)
        }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            updateShizukuUi(animate = true)
        }
    }

    private val shizukuPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                runOnUiThread {
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    updateShizukuUi(animate = true)
                    val msgRes = if (granted) {
                        R.string.msg_shizuku_granted
                    } else {
                        R.string.msg_shizuku_denied
                    }
                    Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val staticImagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importStaticImage(uri)
        }
    }

    private val animatedMediaPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importAnimatedMedia(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PrefManager(this)
        applySystemBarInsets()
        loadInitialState()
        setupViews()
        renderBackgroundList()
        updateActiveBgBadge()

        registerShizukuListeners()
        updateShizukuUi(animate = false)
    }

    override fun onResume() {
        super.onResume()
        updateShizukuUi(animate = false)
    }

    override fun onDestroy() {
        unregisterShizukuListeners()
        super.onDestroy()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun applySystemBarInsets() {
        val baseTop = binding.contentContainer.paddingTop
        val baseBottomBar = binding.bottomActionBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentContainer.updatePadding(top = baseTop + bars.top)
            binding.bottomActionBar.updatePadding(bottom = baseBottomBar + bars.bottom)
            insets
        }
    }

    private fun loadInitialState() {
        selectedBgType = prefManager.bgType
        selectedBgSource = prefManager.bgSource
        selectedPresetId = prefManager.selectedPresetId
        currentTabIsAnimated = (selectedBgType == BgType.GIF || selectedBgType == BgType.VIDEO)

        binding.switchModEnabled.isChecked = prefManager.isModEnabled
        updateModStatusLabel(prefManager.isModEnabled)

        binding.sliderBlur.value = prefManager.glassBlurPx.toFloat()
        binding.tvBlurValue.text = "${prefManager.glassBlurPx}px"

        binding.sliderDim.value = prefManager.overlayDimPercent.toFloat()
        binding.tvDimValue.text = "${prefManager.overlayDimPercent}%"

        binding.switchAmbientGlow.isChecked = prefManager.isAmbientGlowEnabled
        binding.switchHideSuggestions.isChecked = prefManager.isHideSuggestionsEnabled

        binding.toggleBgTab.check(
            if (currentTabIsAnimated) R.id.btnTabAnimated else R.id.btnTabStatic
        )
    }

    private fun setupViews() {
        // Shizuku status card actions
        binding.btnGrantShizuku.setOnClickListener {
            requestShizukuPermission()
        }

        binding.btnOpenShizuku.setOnClickListener {
            openShizukuApp()
        }

        // Master Mod UI toggle (saved immediately to SharedPreferences)
        binding.switchModEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefManager.isModEnabled = isChecked
            updateModStatusLabel(isChecked)
        }

        // Pinned Home Screen Shortcut button
        binding.btnCreateShortcut.setOnClickListener {
            createPinnedGeminiShortcut()
        }

        // Background mode segmented toggle (Static vs Animated)
        binding.toggleBgTab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentTabIsAnimated = (checkedId == R.id.btnTabAnimated)
            renderBackgroundList()
        }

        // Horizontal RecyclerView for background cards
        backgroundAdapter = BackgroundAdapter(
            onAddClicked = { isAnimatedTab ->
                if (isAnimatedTab) {
                    animatedMediaPicker.launch(
                        arrayOf("image/gif", "video/mp4", "video/webm")
                    )
                } else {
                    staticImagePicker.launch(
                        arrayOf("image/jpeg", "image/png", "image/webp")
                    )
                }
            },
            onMediaSelected = { tile ->
                selectedBgType = tile.type
                selectedBgSource = tile.source
                if (tile.source == BgSource.PRESET) {
                    selectedPresetId = tile.id
                }
                renderBackgroundList()
                updateActiveBgBadge()
            }
        )

        binding.rvBackgrounds.apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = backgroundAdapter
            itemAnimator = null
        }

        // Glass Blur slider (0px .. 30px)
        binding.sliderBlur.addOnChangeListener { _, value, _ ->
            binding.tvBlurValue.text = "${value.toInt()}px"
        }

        // Overlay Dim slider (0% .. 80%)
        binding.sliderDim.addOnChangeListener { _, value, _ ->
            binding.tvDimValue.text = "${value.toInt()}%"
        }

        // Bottom CTA: Save all settings & launch GeminiLauncherActivity
        binding.btnSaveAndOpen.setOnClickListener {
            saveAllSettings()
            openGeminiLauncher()
        }
    }

    private fun updateModStatusLabel(enabled: Boolean) {
        binding.tvModStatus.text = if (enabled) {
            "Bật Mod UI • Hiệu ứng Liquid Glass"
        } else {
            "Đã tắt Mod UI • Giao diện web gốc"
        }
        val alpha = if (enabled) 1.0f else 0.52f
        binding.cardWallpaperPicker.alpha = alpha
        binding.cardModEditor.alpha = alpha
    }

    private fun updateActiveBgBadge() {
        binding.tvActiveBgBadge.text = when (selectedBgType) {
            BgType.STATIC -> if (selectedBgSource == BgSource.CUSTOM) "ẢNH TÙY CHỈNH" else "ẢNH TĨNH"
            BgType.GIF -> if (selectedBgSource == BgSource.CUSTOM) "GIF TÙY CHỈNH" else "GIF ĐỘNG"
            BgType.VIDEO -> "VIDEO MP4"
        }
    }

    private fun renderBackgroundList() {
        val list = mutableListOf<BgOptionItem>()
        val cacheVersion = prefManager.customMediaVersion

        if (!currentTabIsAnimated) {
            // Tab 1: Static Image
            list.add(
                BgOptionItem.AddTile(
                    label = getString(R.string.add_static_image),
                    subLabel = "JPG • PNG • WEBP",
                    isAnimatedTab = false
                )
            )

            val customStatic = prefManager.getStaticBgFile()
            if (customStatic.exists() && customStatic.length() > 0L) {
                list.add(
                    BgOptionItem.MediaTile(
                        id = CUSTOM_STATIC_ID,
                        title = "Ảnh của bạn",
                        type = BgType.STATIC,
                        source = BgSource.CUSTOM,
                        customFile = customStatic,
                        cacheKeySuffix = cacheVersion,
                        isSelected = (selectedBgSource == BgSource.CUSTOM && selectedBgType == BgType.STATIC)
                    )
                )
            }

            PrefManager.STATIC_PRESETS.forEach { preset ->
                list.add(
                    BgOptionItem.MediaTile(
                        id = preset.id,
                        title = preset.title,
                        type = preset.type,
                        source = BgSource.PRESET,
                        presetAssetUri = preset.assetUri,
                        isSelected = (selectedBgSource == BgSource.PRESET &&
                            selectedBgType == BgType.STATIC &&
                            selectedPresetId == preset.id)
                    )
                )
            }
        } else {
            // Tab 2: Animated GIF / Video
            list.add(
                BgOptionItem.AddTile(
                    label = getString(R.string.add_animated_media),
                    subLabel = "GIF • MP4 • WEBM",
                    isAnimatedTab = true
                )
            )

            val customVideo = prefManager.getAnimatedVideoFile()
            if (customVideo.exists() && customVideo.length() > 0L) {
                list.add(
                    BgOptionItem.MediaTile(
                        id = CUSTOM_VIDEO_ID,
                        title = "Video của bạn",
                        type = BgType.VIDEO,
                        source = BgSource.CUSTOM,
                        customFile = customVideo,
                        cacheKeySuffix = cacheVersion,
                        isSelected = (selectedBgSource == BgSource.CUSTOM && selectedBgType == BgType.VIDEO)
                    )
                )
            }

            val customGif = prefManager.getAnimatedGifFile()
            if (customGif.exists() && customGif.length() > 0L) {
                list.add(
                    BgOptionItem.MediaTile(
                        id = CUSTOM_GIF_ID,
                        title = "GIF của bạn",
                        type = BgType.GIF,
                        source = BgSource.CUSTOM,
                        customFile = customGif,
                        cacheKeySuffix = cacheVersion,
                        isSelected = (selectedBgSource == BgSource.CUSTOM && selectedBgType == BgType.GIF)
                    )
                )
            }

            PrefManager.ANIMATED_PRESETS.forEach { preset ->
                list.add(
                    BgOptionItem.MediaTile(
                        id = preset.id,
                        title = preset.title,
                        type = preset.type,
                        source = BgSource.PRESET,
                        presetAssetUri = preset.assetUri,
                        isSelected = (selectedBgSource == BgSource.PRESET &&
                            selectedBgType == BgType.GIF &&
                            selectedPresetId == preset.id)
                    )
                )
            }
        }

        backgroundAdapter.submitList(list)
    }

    private fun importStaticImage(uri: Uri) {
        lifecycleScope.launch {
            val targetFile = prefManager.getStaticBgFile()
            val success = copyUriToFile(uri, targetFile)
            if (success) {
                selectedBgType = BgType.STATIC
                selectedBgSource = BgSource.CUSTOM
                prefManager.customMediaVersion = System.currentTimeMillis()
                prefManager.bgType = BgType.STATIC
                prefManager.bgSource = BgSource.CUSTOM
                renderBackgroundList()
                updateActiveBgBadge()
                Toast.makeText(this@MainActivity, R.string.msg_static_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.msg_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importAnimatedMedia(uri: Uri) {
        lifecycleScope.launch {
            val isGif = isGifUri(uri)
            val targetFile = if (isGif) {
                prefManager.getAnimatedGifFile()
            } else {
                prefManager.getAnimatedVideoFile()
            }

            val success = copyUriToFile(uri, targetFile)
            if (success) {
                val chosenType = if (isGif) BgType.GIF else BgType.VIDEO
                selectedBgType = chosenType
                selectedBgSource = BgSource.CUSTOM
                prefManager.lastCustomAnimatedType = chosenType
                prefManager.customMediaVersion = System.currentTimeMillis()
                prefManager.bgType = chosenType
                prefManager.bgSource = BgSource.CUSTOM
                renderBackgroundList()
                updateActiveBgBadge()
                val msgRes = if (isGif) R.string.msg_gif_saved else R.string.msg_video_saved
                Toast.makeText(this@MainActivity, msgRes, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.msg_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isGifUri(uri: Uri): Boolean {
        val mime = contentResolver.getType(uri)?.lowercase()
        if (mime == "image/gif") return true
        if (mime?.startsWith("video/") == true) return false

        val displayName = getDisplayName(uri)?.lowercase()
        return displayName?.endsWith(".gif") == true
    }

    private fun getDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    private suspend fun copyUriToFile(uri: Uri, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempFile = File(destination.parentFile, "${destination.name}.tmp")
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024)
                    }
                } ?: return@withContext false

                if (destination.exists()) {
                    destination.delete()
                }
                tempFile.renameTo(destination)
            }.getOrDefault(false)
        }

    private fun createPinnedGeminiShortcut() {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, R.string.msg_shortcut_unsupported, Toast.LENGTH_LONG).show()
            return
        }

        // Save current settings before pinning so the shortcut immediately reflects user's config
        saveAllSettings()

        val launchIntent = Intent(this, GeminiLauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(this, SHORTCUT_ID_GEMINI)
            .setShortLabel(getString(R.string.gemini_shortcut_label))
            .setLongLabel(getString(R.string.gemini_shortcut_long_label))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_gemini_shortcut))
            .setIntent(launchIntent)
            .build()

        val callbackIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            SHORTCUT_REQUEST_CODE,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val requested = ShortcutManagerCompat.requestPinShortcut(
            this,
            shortcutInfo,
            pendingIntent.intentSender
        )
        if (requested) {
            Toast.makeText(this, R.string.msg_shortcut_requested, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAllSettings() {
        prefManager.saveAll(
            modEnabled = binding.switchModEnabled.isChecked,
            type = selectedBgType,
            source = selectedBgSource,
            presetId = selectedPresetId,
            blurPx = binding.sliderBlur.value.toInt(),
            dimPercent = binding.sliderDim.value.toInt(),
            ambientGlow = binding.switchAmbientGlow.isChecked,
            hideSuggestions = binding.switchHideSuggestions.isChecked
        )
    }

    private fun openGeminiLauncher() {
        val intent = Intent(this, GeminiLauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // Shizuku Integration
    // -------------------------------------------------------------------------

    private fun registerShizukuListeners() {
        Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
    }

    private fun unregisterShizukuListeners() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
    }

    private fun updateShizukuUi(animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(binding.shizukuCardContent)
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val hasPermission = binderAlive && runCatching {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (binderAlive && hasPermission) {
            binding.tvShizukuTitle.setText(R.string.shizuku_title_connected)
            binding.tvShizukuSubtitle.visibility = View.GONE
            binding.layoutShizukuActions.visibility = View.GONE

            binding.tvShizukuBadge.setText(R.string.shizuku_badge_connected)
            binding.tvShizukuBadge.setBackgroundResource(R.drawable.bg_status_badge_green)
            binding.tvShizukuBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green))

            binding.ivShizukuIcon.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_green))
            binding.cardShizukuStatus.strokeColor =
                ContextCompat.getColor(this, R.color.status_green_stroke)
        } else {
            binding.tvShizukuTitle.setText(R.string.shizuku_title_disconnected)
            binding.tvShizukuSubtitle.visibility = View.VISIBLE
            binding.tvShizukuSubtitle.setText(
                if (binderAlive) {
                    R.string.shizuku_subtitle_need_permission
                } else {
                    R.string.shizuku_subtitle_not_running
                }
            )
            binding.layoutShizukuActions.visibility = View.VISIBLE

            binding.tvShizukuBadge.setText(
                if (binderAlive) {
                    R.string.shizuku_badge_need_permission
                } else {
                    R.string.shizuku_badge_disconnected
                }
            )
            binding.tvShizukuBadge.setBackgroundResource(R.drawable.bg_status_badge_warning)
            binding.tvShizukuBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))

            binding.ivShizukuIcon.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_warning))
            binding.cardShizukuStatus.strokeColor =
                ContextCompat.getColor(this, R.color.surface_stroke)
        }
    }

    private fun requestShizukuPermission() {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            Toast.makeText(this, R.string.msg_shizuku_not_running, Toast.LENGTH_LONG).show()
            return
        }
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) {
            Toast.makeText(this, R.string.msg_shizuku_pre_v11, Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            updateShizukuUi(animate = true)
            Toast.makeText(this, R.string.msg_shizuku_granted, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }.onFailure {
            Toast.makeText(this, R.string.msg_shizuku_not_running, Toast.LENGTH_LONG).show()
        }
    }

    private fun openShizukuApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, R.string.msg_shizuku_not_installed, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val SHORTCUT_ID_GEMINI = "mod_ui_pinned_gemini"
        private const val SHORTCUT_REQUEST_CODE = 2026
        private const val CUSTOM_STATIC_ID = "__custom_static__"
        private const val CUSTOM_GIF_ID = "__custom_gif__"
        private const val CUSTOM_VIDEO_ID = "__custom_video__"
    }
}
