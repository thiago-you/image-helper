package you.thiago.imagehelper

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ImageHandler private constructor(val context: Context) {

    var imageFile: File? = null
    var imageBitmap: Bitmap? = null
    var imageUri: String? = null

    private var imageTitle = ""
        get() = field.takeIf { it.isNotBlank() } ?: generateFileName()

    companion object {
        const val DEFAULT_IMG_MAX_WIDTH = 1280
        const val DEFAULT_IMG_MAX_HEIGHT = 960
        const val DEFAULT_IMG_QUALITY = 80

        const val LOWER_IMG_MAX_WIDTH = 800
        const val LOWER_IMG_MAX_HEIGHT = 600
        const val LOWER_IMG_QUALITY = 70

        private const val DEFAULT_THUMB_WIDTH = 125
        private const val DEFAULT_THUMB_HEIGHT = 125

        private const val APP_FACING_LENS = "android.intent.extras.LENS_FACING_FRONT"
        private const val APP_FACING_CAMERA = "android.intent.extras.CAMERA_FACING"

        private var loading: CircularProgressDrawable? = null

        @JvmStatic
        fun with(context: Context): ImageHandler {
            return ImageHandler(context)
        }
    }

    fun getBitmapBase64(bitmap: Bitmap? = imageBitmap, width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY): String? {
        if (bitmap == null) {
            return null
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ImageHelper.toBase64(bitmap, min(width, LOWER_IMG_MAX_WIDTH), min(height, LOWER_IMG_MAX_HEIGHT), min(quality, LOWER_IMG_QUALITY))
        }

        return ImageHelper.toBase64(bitmap, width, height, quality)
    }

    fun getFileBase64(file: File? = imageFile, width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY): String? {
        if (file == null) {
            return null
        }

        return ImageHelper.toBase64(context, file, min(width, LOWER_IMG_MAX_WIDTH), min(height, LOWER_IMG_MAX_HEIGHT), min(quality, LOWER_IMG_QUALITY))
    }

    @Throws(IOException::class)
    fun getUriBase64(uriString: String? = imageUri): String? {
        if (uriString == null) {
            return null
        }

        Uri.parse(uriString)?.takeIf { it.path != null }?.also { uri ->
            getImageBitmap(uri).also {
                return getBitmapBase64(it)
            }
        }

        return null
    }

    @Throws(IOException::class)
    @Suppress("DEPRECATION")
    fun getImageBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    @Throws(IOException::class)
    fun getBitmapFromUri(imgUri: String? = imageUri): Bitmap? {
        if (imgUri == null) {
            return null
        }

        Uri.parse(imgUri)?.takeIf { it.path != null }?.also { uri ->
            return createResizedBitmap(uri)
        }

        return null
    }

    fun getBase64ToBitmap(base64: String): Bitmap? {
        return ImageHelper.toBitmap(base64)
    }

    fun getThumbBitmap(base64: String, thumbWidth: Int = DEFAULT_THUMB_WIDTH, thumbHeight: Int = DEFAULT_THUMB_HEIGHT): Bitmap? {
        return ImageHelper.scaleDown(getBase64ToBitmap(base64), thumbWidth, thumbHeight)
    }

    fun getThumbFromVideo(file: String, thumbWidth: Int = DEFAULT_THUMB_WIDTH, thumbHeight: Int = DEFAULT_THUMB_HEIGHT): Bitmap? {
        return ImageHelper.getThumbFromVideo(file, thumbWidth, thumbHeight)
    }

    fun getBitmapFromRes(res: Resources, @IdRes resId: Int): Bitmap? {
        return BitmapFactory.decodeResource(res, resId)
    }

    fun nullifyMemory() {
        imageFile = null
        imageBitmap = null
        imageUri = null
    }

    fun scaleDown(bitmap: Bitmap? = imageBitmap, maxWidth: Int = DEFAULT_IMG_MAX_WIDTH, maxHeight: Int = DEFAULT_IMG_MAX_HEIGHT): Bitmap? {
        if (bitmap == null) {
            return null
        }

        return ImageHelper.scaleDown(bitmap, maxWidth, maxHeight)
    }

    @Throws(IOException::class)
    fun createImageFile(): ImageHandler {
        nullifyMemory()

        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.also { storageDir ->
            if (!storageDir.exists() && !storageDir.mkdir()) {
                throw IOException(context.getString(R.string.img_dir_not_found))
            }

            imageFile = File.createTempFile(generateFileName(), ".jpeg", storageDir)
        }

        return this
    }

    fun createResizedBitmap(uri: Uri, width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY): Bitmap? {
        ImageHelper.resizeImage(context, uri, imageTitle, width, height, quality).also {
            imageBitmap = it.bitmap
            imageUri = it.uri
        }

        return imageBitmap
    }

    fun createResizedBitmapFromFile(file: File? = imageFile, width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY): ImageHandler {
        if (file == null) {
            return this
        }

        ImageHelper.resizeImage(context, Uri.fromFile(file), width, height, quality).also {
            imageBitmap = it.bitmap
            imageUri = it.uri
        }

        return this
    }

    fun storeImage(width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY): ImageHandler {
        return storeImage(imageFile, width, height, quality)
    }

    fun storeImage(file: File?, width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY): ImageHandler {
        if (file == null) {
            return this
        }

        val uri = Uri.fromFile(file)

        val image = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            ImageHelper.createBitmap(context, uri, imageTitle, min(width, LOWER_IMG_MAX_WIDTH), min(height, LOWER_IMG_MAX_HEIGHT), min(quality, LOWER_IMG_QUALITY))
        } else {
            ImageHelper.createBitmap(context, uri, imageTitle, width, height, quality)
        }

        imageBitmap = image.bitmap
        imageUri = image.uri

        return this
    }

    fun configCameraIntent(facingFront: Boolean = false): Intent {
        if (imageUri != null) {
            return configCameraIntent(Uri.parse(imageUri), facingFront)
        }

        return configCameraIntent(getUriFromFile(), facingFront)
    }

    fun configCameraIntent(uri: Uri? = null, facingFront: Boolean = false): Intent {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        setCameraPosition(intent, facingFront)

        if (uri != null) {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }

        return intent
    }

    fun configGalleryIntent(): Intent {
        return Intent.createChooser(getIntentForGallery(), context.getString(R.string.select_img))
    }

    fun configMultiGalleryIntent(): Intent {
        val intent = getIntentForGallery().apply {
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        return Intent.createChooser(intent, context.getString(R.string.select_img))
    }

    fun loadImage(imageView: ImageView, uri: Uri? = null, bitmap: Bitmap? = imageBitmap, @DrawableRes errorPlaceholder: Int? = null) {
        var glide = if (uri != null || imageUri != null) {
            Glide.with(context).load(uri ?: Uri.parse(imageUri))
        } else {
            Glide.with(context).load(bitmap)
        }

        glide = glide.diskCacheStrategy(DiskCacheStrategy.NONE).fitCenter()

        getLoadingDrawable()?.also { loading ->
            glide = glide.placeholder(loading)
        }

        if (errorPlaceholder != null) {
            glide = glide.apply(RequestOptions().error(errorPlaceholder).centerCrop())
        }

        glide.into(imageView)
    }

    fun build(imageView: ImageView, uri: Uri? = null, width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY, @DrawableRes errorPlaceholder: Int? = null, action: ((uri: String) -> Unit)? = null) {
        if (context !is AppCompatActivity) {
            throw Exception("Context has no lifecycle scope. Expected: AppCompatActivity or Fragment")
        }

        build(context.lifecycleScope, imageView, uri, width, height, quality, errorPlaceholder, action)
    }

    fun build(scope: CoroutineScope, imageView: ImageView, uri: Uri? = null, width: Int = DEFAULT_IMG_MAX_WIDTH, height: Int = DEFAULT_IMG_MAX_HEIGHT, quality: Int = DEFAULT_IMG_QUALITY, @DrawableRes errorPlaceholder: Int? = null, action: ((uri: String) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            if (uri != null) {
                createResizedBitmap(uri, width, height, quality)
            } else {
                storeImage(width, height, quality)
            }

            scope.launch(Dispatchers.Main) {
                loadImage(imageView, errorPlaceholder = errorPlaceholder)
                action?.invoke(imageUri ?: "")
            }
        }
    }

    fun createIntentForImageFile(facingFront: Boolean = false, action: (intent: Intent) -> Unit) {
        if (context !is AppCompatActivity) {
            throw Exception("Context has no lifecycle scope. Expected: AppCompatActivity or Fragment")
        }

        createIntentForImageFile(context.lifecycleScope, facingFront, action)
    }

    fun createIntentForImageFile(scope: CoroutineScope, facingFront: Boolean = false, action: (intent: Intent) -> Unit) {
        scope.launch(Dispatchers.IO) {
            createImageFile()

            scope.launch(Dispatchers.Main) {
                action.invoke(configCameraIntent(facingFront))
            }
        }
    }

    private fun setCameraPosition(intent: Intent, facingFront: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent.putExtra(APP_FACING_LENS, 1.takeIf { facingFront } ?: 0)
        } else {
            intent.putExtra(APP_FACING_CAMERA, 1.takeIf { facingFront } ?: 0)
        }
    }

    private fun getUriFromFile(file: File? = imageFile): Uri? {
        if (file == null) {
            return null
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }

        return Uri.fromFile(file)
    }

    private fun getIntentForGallery(): Intent {
        return Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }

    @Synchronized
    private fun getLoadingDrawable(): CircularProgressDrawable? {
        if (loading == null) {
            loading = CircularProgressDrawable(context).also {
                it.strokeWidth = 5f
                it.centerRadius = 30f
                it.setColorSchemeColors(ContextCompat.getColor(context, R.color.color_secondary))
                it.start()
            }
        }

        return loading
    }

    @Synchronized
    private fun generateFileName(): String {
        return ("img_" + SimpleDateFormat("yyyyMMddhhmmss", Locale.US).format(Date())).also {
            imageTitle = it
        }
    }
}