package eu.kanade.tachiyomi.util.waifu2x

import android.content.Context
import android.graphics.Bitmap
import eu.kanade.tachiyomi.util.qnn.QualcommHtp
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Waifu2x image upscaler using ncnn.
 * Provides 2x upscaling with denoising for manga images.
 */
object Waifu2x {

    const val PROCESSING_BACKEND_VULKAN = 0
    const val PROCESSING_BACKEND_QUALCOMM_NPU = 1
    const val MODEL_REAL_ESRGAN_ANIME = 2
    const val MODEL_W2XEX_PHOTO_SMALL = 9
    const val MODEL_SPAN_NOMOSUNI_PHOTO = 19
    const val REAL_ESRGAN_STYLE_ANIME = 0
    const val REAL_ESRGAN_STYLE_PHOTO = 1

    // Bump when bundled model assets change so existing installations refresh their cache.
    private const val BUNDLED_MODEL_CACHE_VERSION = "14"
    private const val QNN_CONTEXT_CACHE_VERSION = "17"

    @Volatile private var isInitialized = false

    @Volatile private var isRealCuganInitialized = false

    @Volatile private var isRealEsrganInitialized = false

    @Volatile private var isNoseInitialized = false

    @Volatile private var isWaifu2xInitialized = false

    @Volatile private var isAnime4kInitialized = false

    @Volatile private var isW2xExInitialized = false

    init {
        try {
            System.loadLibrary("waifu2x-jni")
        } catch (e: UnsatisfiedLinkError) {
            // Native library not available
        }
    }

    fun init(context: Context, noiseLevel: Int = 2, scale: Int = 2): Boolean {
        if (isInitialized) return true

        return synchronized(this) {
            if (isInitialized) return true

            val modelDir = extractModelsToCache(context, "waifu2x-models")
            if (modelDir == null) {
                return false
            }

            isInitialized = nativeInit(modelDir, noiseLevel, scale, 0, false)
            if (isInitialized) {
                // Invalidate all other models
                isRealCuganInitialized = false
                isRealEsrganInitialized = false
                isNoseInitialized = false
                isWaifu2xInitialized = false // Wait, I am Waifu2x (generic)
                isAnime4kInitialized = false
                isW2xExInitialized = false
            }
            isInitialized
        }
    }

    /**
     * Process a bitmap image with Waifu2x upscaling.
     *
     * @param input Input bitmap (will not be modified)
     * @return Upscaled bitmap, or null if processing failed
     */
    fun process(input: Bitmap, id: Int = -1): Bitmap? {
        if (!isInitialized) return null

        // Ensure input is in ARGB_8888 format
        val argbBitmap = if (input.config != Bitmap.Config.ARGB_8888) {
            input.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            input
        }

        return nativeProcess(argbBitmap, id)
    }

    // Track current config to detect changes (excludes tileSleepMs since that doesn't require model reload)
    private data class RealCuganConfig(
        val noise: Int,
        val scale: Int,
        val isPro: Boolean,
        val precision: Int,
        val fp16Arithmetic: Boolean,
        val processingBackend: Int,
    )

    @Volatile private var lastRealCuganConfig: RealCuganConfig? = null

    fun initRealCugan(
        context: Context,
        noiseLevel: Int,
        scale: Int,
        isPro: Boolean = false,
        tileSleepMs: Int = 0,
        tileSize: Int = 128,
        precision: Int = 0,
        fp16Arithmetic: Boolean = false,
        processingBackend: Int = PROCESSING_BACKEND_VULKAN,
    ): Boolean {
        val effectiveNoiseLevel = if (isPro && noiseLevel !in setOf(0, 3, 4)) 3 else noiseLevel
        val model = if (isPro) 1 else 0
        val resolvedBackend = resolveProcessingBackend(processingBackend, model, scale)
        val resolvedPrecision = resolvePrecision(precision, resolvedBackend, model, scale)
        val newConfig = RealCuganConfig(
            effectiveNoiseLevel,
            scale,
            isPro,
            resolvedPrecision,
            fp16Arithmetic,
            resolvedBackend,
        )

        // Fast path: if already initialized with same config, just update performance params and return
        if (isRealCuganInitialized && lastRealCuganConfig == newConfig) {
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
            return true
        }

        return synchronized(this) {
            val currentConfig = RealCuganConfig(
                effectiveNoiseLevel,
                scale,
                isPro,
                resolvedPrecision,
                fp16Arithmetic,
                resolvedBackend,
            )

            // Force reinit only if model parameters changed (not tileSleepMs)
            if (lastRealCuganConfig != currentConfig) {
                android.util.Log.d("Waifu2x", "Config changed from $lastRealCuganConfig to $currentConfig, reinitializing...")
                isRealCuganInitialized = false
            }

            if (isRealCuganInitialized) {
                // Model already loaded, just update performance params
                nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
                return true
            }

            val assetPath = if (isPro) "realcugan-pro-models" else "realcugan-models"
            val modelDir = extractModelsToCache(context, assetPath)
            if (modelDir == null) {
                return false
            }

            isRealCuganInitialized = nativeInitRealCugan(
                modelDir,
                effectiveNoiseLevel,
                scale,
                tileSleepMs,
                currentConfig.precision,
                currentConfig.fp16Arithmetic,
            )
            if (isRealCuganInitialized) {
                if (currentConfig.processingBackend == PROCESSING_BACKEND_QUALCOMM_NPU) {
                    val variant = when (effectiveNoiseLevel) {
                        1 -> "denoise1x"
                        2 -> "denoise2x"
                        3 -> "denoise3x"
                        4 -> "conservative"
                        else -> "no-denoise"
                    }
                    val precisionSuffix = if (currentConfig.precision == 2) "-int8" else ""
                    val family = if (isPro) "pro" else "se"
                    initializeQnnIfAvailable(
                        context,
                        "realcugan-$family-x$scale-$variant$precisionSuffix",
                        padding = if (scale == 3) 14 else 18,
                    )
                }
                lastRealCuganConfig = currentConfig
                nativeUpdatePerformanceConfig(tileSleepMs, tileSize)

                // Invalidate all other models
                isInitialized = false
                isRealEsrganInitialized = false
                isNoseInitialized = false
                isWaifu2xInitialized = false
                isAnime4kInitialized = false
                isW2xExInitialized = false

                android.util.Log.d(
                    "Waifu2x",
                    "Initialized Real-CUGAN: isPro=$isPro, noise=$effectiveNoiseLevel, scale=$scale, " +
                        "tileSleepMs=$tileSleepMs, tileSize=$tileSize, precision=${currentConfig.precision}, " +
                        "backend=${backendName(currentConfig.processingBackend)}",
                )
            }
            isRealCuganInitialized
        }
    }

    // Track Real-ESRGAN config
    private data class RealEsrganConfig(val style: Int, val scale: Int, val precision: Int, val fp16Arithmetic: Boolean, val processingBackend: Int)
    private var lastRealEsrganConfig: RealEsrganConfig? = null

    fun initRealESRGAN(
        context: Context,
        scale: Int,
        style: Int = REAL_ESRGAN_STYLE_ANIME,
        tileSleepMs: Int = 0,
        tileSize: Int = 128,
        precision: Int = 0,
        fp16Arithmetic: Boolean = false,
        processingBackend: Int = PROCESSING_BACKEND_VULKAN,
    ): Boolean = synchronized(this) {
        val isPhotoStyle = style == REAL_ESRGAN_STYLE_PHOTO
        val outputScale = if (isPhotoStyle) 2 else scale
        val resolvedBackend = resolveProcessingBackend(processingBackend, MODEL_REAL_ESRGAN_ANIME, outputScale)
        val resolvedPrecision = resolvePrecision(precision, resolvedBackend, MODEL_REAL_ESRGAN_ANIME, outputScale)
        val config = RealEsrganConfig(style, outputScale, resolvedPrecision, fp16Arithmetic, resolvedBackend)
        // Force reinit if config changed
        if (lastRealEsrganConfig != config) {
            android.util.Log.d("Waifu2x", "Real-ESRGAN config changed from $lastRealEsrganConfig to $config, reinitializing...")
            isRealEsrganInitialized = false
        }

        if (isRealEsrganInitialized) {
            // Update throttling
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
            return true
        }

        val assetPath = if (isPhotoStyle) {
            "realesrgan-models/v3-general"
        } else {
            "realesrgan-models/v3-anime"
        }
        val modelDir = extractModelsToCache(context, assetPath)
        if (modelDir == null) {
            return false
        }

        val modelScale = if (isPhotoStyle) 4 else outputScale
        isRealEsrganInitialized = nativeInitRealESRGAN(
            modelDir,
            modelScale,
            outputScale,
            config.precision,
            config.fp16Arithmetic,
        )
        if (isRealEsrganInitialized) {
            if (config.processingBackend == PROCESSING_BACKEND_QUALCOMM_NPU) {
                initializeQnnIfAvailable(
                    context,
                    if (isPhotoStyle) {
                        if (config.precision == 2) {
                            "realesrgan-general-x4v3-x2-int8"
                        } else {
                            "realesrgan-general-x4v3-x2"
                        }
                    } else {
                        if (config.precision == 2) {
                            "realesrgan-animevideov3-x2-int8"
                        } else {
                            "realesrgan-animevideov3-x2"
                        }
                    },
                    padding = if (isPhotoStyle) 32 else 16,
                )
            }
            lastRealEsrganConfig = config
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)

            // Invalidate all other models
            isInitialized = false
            isRealCuganInitialized = false
            isNoseInitialized = false
            isWaifu2xInitialized = false
            isAnime4kInitialized = false
            isW2xExInitialized = false

            val variant = if (isPhotoStyle) "general-x4v3" else "animevideov3"
            android.util.Log.d("Waifu2x", "Initialized Real-ESRGAN $variant: outputScale=$outputScale, tileSleepMs=$tileSleepMs, tileSize=$tileSize, precision=${config.precision}, backend=${backendName(config.processingBackend)}")
        }
        isRealEsrganInitialized
    }

    private data class GenericModelConfig(val precision: Int, val fp16Arithmetic: Boolean)
    private var lastNoseConfig: GenericModelConfig? = null

    fun initNose(context: Context, tileSleepMs: Int = 0, tileSize: Int = 128, precision: Int = 0, fp16Arithmetic: Boolean = false): Boolean = synchronized(this) {
        val config = GenericModelConfig(precision.coerceIn(0, 3), fp16Arithmetic)
        if (lastNoseConfig != config) {
            isNoseInitialized = false
        }
        if (isNoseInitialized) {
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
            return true
        }

        val modelDir = extractModelsToCache(context, "waifu2x-models-nose")
        if (modelDir == null) {
            return false
        }

        isNoseInitialized = nativeInitNose(modelDir, config.precision, config.fp16Arithmetic)
        if (isNoseInitialized) {
            lastNoseConfig = config
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)

            // Invalidate all other models
            isInitialized = false
            isRealCuganInitialized = false
            isRealEsrganInitialized = false
            isWaifu2xInitialized = false
            isAnime4kInitialized = false
            isW2xExInitialized = false

            android.util.Log.d("Waifu2x", "Initialized Nose model, tileSleepMs=$tileSleepMs, tileSize=$tileSize, precision=${config.precision}")
        }
        isNoseInitialized
    }

    // Track Waifu2x config
    private data class Waifu2xConfig(val noise: Int, val scale: Int, val precision: Int, val fp16Arithmetic: Boolean)
    private var lastWaifu2xConfig: Waifu2xConfig? = null

    fun initWaifu2x(context: Context, noise: Int, scale: Int, tileSleepMs: Int = 0, tileSize: Int = 128, precision: Int = 0, fp16Arithmetic: Boolean = false): Boolean = synchronized(this) {
        val newConfig = Waifu2xConfig(noise, scale, precision.coerceIn(0, 3), fp16Arithmetic)

        // Force reinit if config changed
        if (lastWaifu2xConfig != newConfig) {
            android.util.Log.d("Waifu2x", "Waifu2x config changed from $lastWaifu2xConfig to $newConfig, reinitializing...")
            isWaifu2xInitialized = false
        }

        if (isWaifu2xInitialized) {
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
            return true
        }

        val modelDir = extractModelsToCache(context, "waifu2x-models")
        if (modelDir == null) return false

        isWaifu2xInitialized = nativeInit(modelDir, noise, scale, newConfig.precision, newConfig.fp16Arithmetic)
        if (isWaifu2xInitialized) {
            lastWaifu2xConfig = newConfig
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)

            // Invalidate all other models
            isInitialized = false
            isRealCuganInitialized = false
            isRealEsrganInitialized = false
            isNoseInitialized = false
            isAnime4kInitialized = false
            isW2xExInitialized = false

            android.util.Log.d("Waifu2x", "Initialized Waifu2x: noise=$noise, scale=$scale, tileSleepMs=$tileSleepMs, tileSize=$tileSize, precision=${newConfig.precision}")
        }
        isWaifu2xInitialized
    }

    fun initWaifu2xUpconv7(context: Context, noise: Int, scale: Int, tileSleepMs: Int = 0, tileSize: Int = 128, precision: Int = 0, fp16Arithmetic: Boolean = false): Boolean = synchronized(this) {
        val newConfig = Waifu2xConfig(noise, scale, precision.coerceIn(0, 3), fp16Arithmetic)

        // Force reinit if config changed
        if (lastWaifu2xConfig != newConfig) {
            android.util.Log.d("Waifu2x", "Waifu2x UpConv7 config changed from $lastWaifu2xConfig to $newConfig, reinitializing...")
            isWaifu2xInitialized = false
        }

        if (isWaifu2xInitialized) {
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
            return true
        }

        val modelDir = extractModelsToCache(context, "waifu2x-models-upconv7")
        if (modelDir == null) return false

        isWaifu2xInitialized = nativeInitWaifu2xUpconv7(modelDir, noise, scale, newConfig.precision, newConfig.fp16Arithmetic)
        if (isWaifu2xInitialized) {
            lastWaifu2xConfig = newConfig
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)

            // Invalidate all other models
            isInitialized = false
            isRealCuganInitialized = false
            isRealEsrganInitialized = false
            isNoseInitialized = false
            isAnime4kInitialized = false
            isW2xExInitialized = false

            android.util.Log.d("Waifu2x", "Initialized Waifu2x UpConv7: noise=$noise, scale=$scale, tileSleepMs=$tileSleepMs, tileSize=$tileSize, precision=${newConfig.precision}")
        }
        isWaifu2xInitialized
    }

    private data class W2xExConfig(
        val model: Int,
        val scale: Int,
        val precision: Int,
        val fp16Arithmetic: Boolean,
        val processingBackend: Int,
    )
    private var lastW2xExConfig: W2xExConfig? = null

    private data class W2xExModel(
        val stem: String,
        val scale: Int,
        val assetPath: String,
        val padding: Int = 10,
    )

    private fun w2xExModel(
        stem: String,
        scale: Int,
        family: String,
        padding: Int = 10,
    ): W2xExModel {
        return W2xExModel(stem, scale, "$family/$stem", padding)
    }

    private fun w2xExModelFor(model: Int): W2xExModel? {
        return when (model) {
            6 -> w2xExModel("Universal-Fast-W2xEX", 2, "w2xex-esrgan")
            8 -> w2xExModel("Omni-MiniV2-W2xEX", 2, "w2xex-esrgan")
            MODEL_W2XEX_PHOTO_SMALL -> w2xExModel("Photo-Small-W2xEX", 2, "w2xex-esrgan")
            16 -> w2xExModel("animejanai-v2-ultra-compact-x2", 2, "animejanai-ncnn-vulkan")
            18 -> w2xExModel("2x-sudo-UltraCompact", 2, "sudo-ultracompact")
            MODEL_SPAN_NOMOSUNI_PHOTO ->
                w2xExModel("2x-NomosUni-SPAN-multijpg-ldl", 2, "span-nomosuni", padding = 24)
            else -> null
        }
    }

    fun w2xExScaleFor(model: Int): Int? = w2xExModelFor(model)?.scale

    fun isW2xExModel(model: Int): Boolean = w2xExModelFor(model) != null

    fun initW2xEx(
        context: Context,
        model: Int,
        scale: Int = 2,
        tileSleepMs: Int = 0,
        tileSize: Int = 128,
        precision: Int = 0,
        fp16Arithmetic: Boolean = false,
        processingBackend: Int = PROCESSING_BACKEND_VULKAN,
    ): Boolean = synchronized(this) {
        val selectedModel = w2xExModelFor(model) ?: return false
        val effectiveScale = selectedModel.scale
        val modelDir = extractModelsToCache(context, selectedModel.assetPath) ?: return false
        val modelStem = selectedModel.stem
        val resolvedBackend = resolveProcessingBackend(processingBackend, model, effectiveScale)
        val resolvedPrecision = resolvePrecision(precision, resolvedBackend, model, effectiveScale)

        val config = W2xExConfig(model, effectiveScale, resolvedPrecision, fp16Arithmetic, resolvedBackend)
        if (lastW2xExConfig != config) {
            isW2xExInitialized = false
        }

        if (isW2xExInitialized) {
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
            return true
        }

        isW2xExInitialized = nativeInitW2xEx(
            modelDir,
            modelStem,
            effectiveScale,
            config.precision,
            config.fp16Arithmetic,
            selectedModel.padding,
        )
        if (isW2xExInitialized) {
            val qnnModel = when (model) {
                MODEL_W2XEX_PHOTO_SMALL -> "w2xex-photo-small-x2"
                MODEL_SPAN_NOMOSUNI_PHOTO -> "span-nomosuni-x2"
                else -> null
            }
            if (config.processingBackend == PROCESSING_BACKEND_QUALCOMM_NPU && qnnModel != null) {
                initializeQnnIfAvailable(
                    context,
                    if (config.precision == 2) "$qnnModel-int8" else qnnModel,
                    padding = selectedModel.padding,
                )
            }
            lastW2xExConfig = config
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)

            isInitialized = false
            isRealCuganInitialized = false
            isRealEsrganInitialized = false
            isNoseInitialized = false
            isWaifu2xInitialized = false
            isAnime4kInitialized = false

            android.util.Log.d(
                "Waifu2x",
                "Initialized generic ncnn model: $modelStem, scale=$effectiveScale, precision=${config.precision}, backend=${backendName(config.processingBackend)}",
            )
        }
        isW2xExInitialized
    }

    // Reuse processRealCugan for all generic ncnn models
    // But check specific flags
    // Reuse processRealCugan for all generic ncnn models
    // But check specific flags
    fun processRealESRGAN(input: Bitmap, id: Int = -1): Bitmap? {
        if (!isRealEsrganInitialized) return null
        return processBitmapHelper(input, id)
    }

    fun processNose(input: Bitmap, id: Int = -1): Bitmap? {
        if (!isNoseInitialized) return null
        return processBitmapHelper(input, id)
    }

    fun processWaifu2x(input: Bitmap, id: Int = -1): Bitmap? {
        if (!isWaifu2xInitialized) return null
        return processBitmapHelper(input, id)
    }

    fun processW2xEx(input: Bitmap, id: Int = -1): Bitmap? {
        if (!isW2xExInitialized) return null
        return processBitmapHelper(input, id)
    }

    @Volatile var processingId: Int = -1

    private fun processBitmapHelper(input: Bitmap, id: Int): Bitmap? {
        if (input.isRecycled) return null

        val argbBitmap = if (input.config != Bitmap.Config.ARGB_8888) {
            try {
                input.copy(Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                null
            }
        } else {
            input
        } ?: return null

        processingId = id
        try {
            val result = nativeProcessRealCugan(argbBitmap, id)
            return if (result === argbBitmap) null else result
        } finally {
            processingId = -1
            if (argbBitmap !== input) {
                argbBitmap.recycle()
            }
        }
    }

    /**
     * Get the raw packed progress value from native code.
     * Format: [ID (upper 32 bits)] [Progress (lower 32 bits)]
     */
    fun getProgress(): Long = nativeGetProgress()

    /**
     * Get only the progress percentage (0-100) from the packed value.
     */
    fun getProgressPercent(): Int {
        val packed = nativeGetProgress()
        return (packed and 0xFFFFFFFF).toInt()
    }

    /**
     * Get only the processing ID from the packed value.
     */
    fun getProgressId(): Int {
        val packed = nativeGetProgress()
        return (packed shr 32).toInt()
    }

    /**
     * Reset Real-CUGAN to allow re-initialization with new settings.
     */
    fun resetRealCugan() {
        isInitialized = false
        isRealCuganInitialized = false
        isRealEsrganInitialized = false
        isNoseInitialized = false
        isWaifu2xInitialized = false
        isAnime4kInitialized = false
        isW2xExInitialized = false
        lastRealCuganConfig = null
        lastRealEsrganConfig = null
        lastNoseConfig = null
        lastWaifu2xConfig = null
        lastW2xExConfig = null
        nativeDestroy()
    }

    /**
     * Ask any active native upscaling operation to stop at its next cancellation check.
     */
    fun abortProcessing() {
        nativeAbortProcessing()
    }

    fun prepareProcessing() {
        nativeClearAbortProcessing()
    }

    /**
     * Process bitmap with Real-CUGAN.
     */
    fun processRealCugan(input: Bitmap, id: Int = -1): Bitmap? {
        if (!isRealCuganInitialized) return null
        return processBitmapHelper(input, id)
    }

    /**
     * Release native resources.
     */
    fun destroy() {
        if (isInitialized || isRealCuganInitialized || isRealEsrganInitialized || isNoseInitialized || isWaifu2xInitialized || isAnime4kInitialized || isW2xExInitialized) {
            nativeDestroy()
            isInitialized = false
            isRealCuganInitialized = false
            isRealEsrganInitialized = false
            isNoseInitialized = false
            isWaifu2xInitialized = false
            isAnime4kInitialized = false
            isW2xExInitialized = false
        }
    }

    /**
     * Initialize Anime4K with specific mode.
     */
    fun initAnime4K(context: Context, mode: Int): Boolean {
        if (isAnime4kInitialized) return true

        val assetManager = context.assets
        val shaders = mutableListOf<String>()
        val names = mutableListOf<String>()

        fun addShader(name: String) {
            val content = assetManager.open("anime4k/$name").bufferedReader().use { it.readText() }
            shaders.add(content)
            names.add(name)
        }

        try {
            addShader("Anime4K_Clamp_Highlights.glsl")
            when (mode) {
                0 -> addShader("Anime4K_Restore_CNN_M.glsl") // Fast
                1 -> addShader("Anime4K_Restore_CNN_VL.glsl") // High
                2 -> { // Ultra
                    addShader("Anime4K_Restore_CNN_VL.glsl")
                    addShader("Anime4K_Upscale_CNN_x2_VL.glsl")
                }
            }
        } catch (e: Exception) {
            return false
        }

        isAnime4kInitialized = nativeInitAnime4K(shaders.toTypedArray(), names.toTypedArray())
        // Invalidate all other models
        if (isAnime4kInitialized) {
            isInitialized = false
            isRealCuganInitialized = false
            isRealEsrganInitialized = false
            isNoseInitialized = false
            isWaifu2xInitialized = false
            isW2xExInitialized = false
        }
        return isAnime4kInitialized
    }

    /**
     * Process bitmap with Anime4K.
     */
    fun processAnime4K(input: Bitmap): Bitmap? {
        if (!isAnime4kInitialized || input.isRecycled) return null

        val argbBitmap = try {
            if (input.config != Bitmap.Config.ARGB_8888) {
                input.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                input.copy(Bitmap.Config.ARGB_8888, true) // Must be mutable for in-place
            }
        } catch (e: Exception) {
            null
        } ?: return null

        try {
            return nativeProcessAnime4K(argbBitmap)
        } finally {
            // We don't recycle argbBitmap if it's the same as input,
            // but here it's always a copy (true).
            // Actually, nativeProcessAnime4K returns the SAME bitmap (in-place)
            // so we SHOULD NOT recycle it here if it's the result.
        }
    }

    private fun extractModelsToCache(context: Context, assetPath: String): String? {
        return try {
            val cacheDir = File(context.cacheDir, assetPath)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val assetManager = context.assets
            val modelFiles = assetManager.list(assetPath).orEmpty()
            val assetVersionFile = File(cacheDir, ".bundled-model-version")
            val refreshBundledModels = modelFiles.isNotEmpty() &&
                assetVersionFile.takeIf(File::exists)?.readText() != BUNDLED_MODEL_CACHE_VERSION

            for (filename in modelFiles) {
                val outFile = File(cacheDir, filename)
                if (refreshBundledModels || !outFile.exists()) {
                    assetManager.open("$assetPath/$filename").use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            if (refreshBundledModels) {
                assetVersionFile.writeText(BUNDLED_MODEL_CACHE_VERSION)
            }

            if (!cacheDir.hasNcnnModels()) {
                downloadReleaseModels(assetPath, cacheDir)
            }

            if (!cacheDir.hasNcnnModels()) {
                downloadDirectModels(assetPath, cacheDir)
            }

            if (!cacheDir.hasNcnnModels()) {
                return null
            }

            cacheDir.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("Waifu2x", "Failed to prepare model assets: $assetPath", e)
            null
        }
    }

    private data class ModelReleaseSource(
        val url: String,
        val entryPrefix: String,
        val stripPrefix: String = "",
    )

    private data class DirectModelSource(
        val baseUrl: String,
        val files: List<String>,
    )

    private fun releaseSourceFor(assetPath: String): ModelReleaseSource? {
        return when (assetPath) {
            "realcugan-models" -> ModelReleaseSource(
                url = "https://github.com/nihui/realcugan-ncnn-vulkan/releases/download/20220728/realcugan-ncnn-vulkan-20220728-ubuntu.zip",
                entryPrefix = "realcugan-ncnn-vulkan-20220728-ubuntu/models-se/",
            )
            "realcugan-pro-models" -> ModelReleaseSource(
                url = "https://github.com/nihui/realcugan-ncnn-vulkan/releases/download/20220728/realcugan-ncnn-vulkan-20220728-ubuntu.zip",
                entryPrefix = "realcugan-ncnn-vulkan-20220728-ubuntu/models-pro/",
            )
            "waifu2x-models-nose" -> ModelReleaseSource(
                url = "https://github.com/nihui/realcugan-ncnn-vulkan/releases/download/20220728/realcugan-ncnn-vulkan-20220728-ubuntu.zip",
                entryPrefix = "realcugan-ncnn-vulkan-20220728-ubuntu/models-nose/",
            )
            "waifu2x-models" -> ModelReleaseSource(
                url = "https://github.com/nihui/waifu2x-ncnn-vulkan/releases/download/20250915/waifu2x-ncnn-vulkan-20250915-linux.zip",
                entryPrefix = "waifu2x-ncnn-vulkan-20250915-linux/models-cunet/",
            )
            "waifu2x-models-upconv7" -> ModelReleaseSource(
                url = "https://github.com/nihui/waifu2x-ncnn-vulkan/releases/download/20250915/waifu2x-ncnn-vulkan-20250915-linux.zip",
                entryPrefix = "waifu2x-ncnn-vulkan-20250915-linux/models-upconv_7_anime_style_art_rgb/",
            )
            "realesrgan-models/v3-anime" -> ModelReleaseSource(
                url = "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-ncnn-vulkan-20220424-ubuntu.zip",
                entryPrefix = "models/realesr-animevideov3-",
                stripPrefix = "realesr-animevideov3-",
            )
            else -> null
        }
    }

    private fun directSourceFor(assetPath: String): DirectModelSource? {
        val w2xExStem = assetPath.removePrefix("w2xex-esrgan/").takeIf { it != assetPath }
        if (w2xExStem != null) {
            val supported = setOf(
                "Universal-Fast-W2xEX",
                "Omni-MiniV2-W2xEX",
                "Photo-Small-W2xEX",
            )
            if (w2xExStem !in supported) return null

            return DirectModelSource(
                baseUrl = "https://huggingface.co/randomblock1/W2xEX-ESRGAN/resolve/main",
                files = listOf("$w2xExStem.param", "$w2xExStem.bin"),
            )
        }

        val animeJaNaiStem = assetPath.removePrefix("animejanai-ncnn-vulkan/").takeIf { it != assetPath }
        if (animeJaNaiStem == "animejanai-v2-ultra-compact-x2") {
            return DirectModelSource(
                baseUrl = "https://raw.githubusercontent.com/Justin62628/animejanai-ncnn-vulkan/main/models/$animeJaNaiStem",
                files = listOf("$animeJaNaiStem.param", "$animeJaNaiStem.bin"),
            )
        }

        return null
    }

    private fun downloadReleaseModels(assetPath: String, cacheDir: File) {
        val source = releaseSourceFor(assetPath) ?: return
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            ZipInputStream(BufferedInputStream(connection.inputStream)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.name.startsWith(source.entryPrefix)) {
                        zip.closeEntry()
                        continue
                    }
                    val rawName = entry.name.substringAfterLast('/')
                    if (!rawName.endsWith(".param") && !rawName.endsWith(".bin")) {
                        zip.closeEntry()
                        continue
                    }
                    val filename = rawName.removePrefix(source.stripPrefix)
                    val outFile = File(cacheDir, filename)
                    outFile.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                    zip.closeEntry()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadDirectModels(assetPath: String, cacheDir: File) {
        val source = directSourceFor(assetPath) ?: return
        source.files.forEach { filename ->
            val outFile = File(cacheDir, filename)
            if (outFile.exists() && outFile.length() > 0L) return@forEach

            val tempFile = File(cacheDir, "$filename.tmp")
            val url = "${source.baseUrl}/$filename"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 120_000
                instanceFollowRedirects = true
            }
            try {
                BufferedInputStream(connection.inputStream).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (!tempFile.renameTo(outFile)) {
                    tempFile.delete()
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun File.hasNcnnModels(): Boolean {
        val files = listFiles().orEmpty()
        return files.any { it.extension == "param" } && files.any { it.extension == "bin" }
    }

    fun setUiBusy(busy: Boolean) {
        nativeSetUiBusy(busy)
    }

    fun isQnnRuntimeAvailable(): Boolean = try {
        nativeIsQnnRuntimeAvailable()
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    fun isQualcommNpuAvailable(): Boolean = QualcommHtp.architecture() != null && isQnnRuntimeAvailable()

    fun isQualcommNpuModelSupported(model: Int, scale: Int): Boolean {
        if (model == 1) return scale == 2 || scale == 3
        if (scale != 2) return false
        return model == 0 ||
            model == MODEL_REAL_ESRGAN_ANIME ||
            model == MODEL_W2XEX_PHOTO_SMALL ||
            model == MODEL_SPAN_NOMOSUNI_PHOTO
    }

    fun resolveProcessingBackend(requestedBackend: Int, model: Int, scale: Int): Int {
        return if (
            requestedBackend == PROCESSING_BACKEND_QUALCOMM_NPU &&
            isQualcommNpuModelSupported(model, scale) &&
            isQualcommNpuAvailable()
        ) {
            PROCESSING_BACKEND_QUALCOMM_NPU
        } else {
            PROCESSING_BACKEND_VULKAN
        }
    }

    fun resolvePrecision(requestedPrecision: Int, processingBackend: Int, model: Int, scale: Int): Int {
        val precision = requestedPrecision.coerceIn(0, 3)
        if (resolveProcessingBackend(processingBackend, model, scale) != PROCESSING_BACKEND_QUALCOMM_NPU) {
            return precision
        }
        return if (isQualcommNpuModelSupported(model, scale) && precision == 2) 2 else 0
    }

    private fun backendName(backend: Int): String {
        return if (backend == PROCESSING_BACKEND_QUALCOMM_NPU) "Qualcomm NPU" else "Vulkan"
    }

    fun isQnnActive(): Boolean = try {
        nativeIsQnnInitialized()
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    private fun initializeQnnIfAvailable(context: Context, modelName: String, padding: Int) {
        val htpArchitecture = QualcommHtp.architecture(context) ?: return
        if (!isQnnRuntimeAvailable()) return
        try {
            val filename = "$modelName.v$htpArchitecture.bin"
            val directory = File(context.cacheDir, "qnn-contexts-v$htpArchitecture").apply { mkdirs() }
            val output = File(directory, filename)
            val version = File(directory, ".$filename.version")
            if (!output.isFile || version.takeIf(File::isFile)?.readText() != QNN_CONTEXT_CACHE_VERSION) {
                context.assets.open("qnn-contexts/$filename").use { input ->
                    output.outputStream().use(input::copyTo)
                }
                version.writeText(QNN_CONTEXT_CACHE_VERSION)
            }
            val active = nativeInitQnn(
                output.absolutePath,
                context.applicationInfo.nativeLibraryDir,
                padding,
            )
            android.util.Log.d(
                "Waifu2x",
                "Qualcomm NPU ${if (active) "enabled" else "unavailable"}: $filename (HTP v$htpArchitecture)",
            )
        } catch (e: Exception) {
            android.util.Log.w("Waifu2x", "Unable to initialize Qualcomm NPU; using Vulkan", e)
        }
    }

    fun scaleBitmapNative(input: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (input.isRecycled) return null
        if (input.width == targetWidth && input.height == targetHeight) return input

        val argbBitmap = if (input.config != Bitmap.Config.ARGB_8888) {
            try {
                input.copy(Bitmap.Config.ARGB_8888, false)
            } catch (e: Exception) {
                null
            }
        } else {
            input
        } ?: return null

        return try {
            nativeScaleBitmap(argbBitmap, targetWidth, targetHeight)
        } finally {
            if (argbBitmap !== input) {
                argbBitmap.recycle()
            }
        }
    }

    // Native methods
    private external fun nativeInit(modelDir: String, noiseLevel: Int, scale: Int, precision: Int, fp16Arithmetic: Boolean): Boolean
    private external fun nativeInitWaifu2xUpconv7(modelDir: String, noiseLevel: Int, scale: Int, precision: Int, fp16Arithmetic: Boolean): Boolean
    private external fun nativeInitW2xEx(
        modelDir: String,
        modelStem: String,
        scale: Int,
        precision: Int,
        fp16Arithmetic: Boolean,
        padding: Int,
    ): Boolean
    private external fun nativeProcess(input: Bitmap, id: Int): Bitmap?
    private external fun nativeDestroy()
    private external fun nativeAbortProcessing()
    private external fun nativeClearAbortProcessing()
    private external fun nativeSetUiBusy(busy: Boolean)
    private external fun nativeIsQnnRuntimeAvailable(): Boolean
    private external fun nativeInitQnn(contextPath: String, nativeLibraryDir: String, padding: Int): Boolean
    private external fun nativeIsQnnInitialized(): Boolean

    // ... (Anime4K signatures unchanged)

    private external fun nativeInitAnime4K(shaders: Array<String>, names: Array<String>): Boolean
    private external fun nativeProcessAnime4K(input: Bitmap): Bitmap?

    private external fun nativeInitRealCugan(modelDir: String, noiseLevel: Int, scale: Int, tileSleepMs: Int, precision: Int, fp16Arithmetic: Boolean): Boolean
    private external fun nativeUpdatePerformanceConfig(tileSleepMs: Int, tileSize: Int)

    fun updatePerformance(tileSleepMs: Int, tileSize: Int) {
        if (isRealCuganInitialized || isRealEsrganInitialized || isNoseInitialized || isWaifu2xInitialized || isW2xExInitialized) {
            nativeUpdatePerformanceConfig(tileSleepMs, tileSize)
        }
    }

    private external fun nativeInitRealESRGAN(modelDir: String, modelScale: Int, outputScale: Int, precision: Int, fp16Arithmetic: Boolean): Boolean
    private external fun nativeInitNose(modelDir: String, precision: Int, fp16Arithmetic: Boolean): Boolean
    private external fun nativeProcessRealCugan(input: Bitmap, id: Int): Bitmap?
    private external fun nativeScaleBitmap(input: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap?
    private external fun nativeGetProgress(): Long
}
