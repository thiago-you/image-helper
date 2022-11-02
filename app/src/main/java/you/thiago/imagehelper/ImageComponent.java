package you.thiago.imagehelper;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Component to handle IMG implementations
 */
@SuppressWarnings("unused WeakerAccess")
public class ImageComponent {

    public static final int DEFAULT_IMG_MAX_WIDTH = 1280;
    public static final int DEFAULT_IMG_MAX_HEIGHT = 960;
    public static final int DEFAULT_IMG_QUALITY = 80;

    public static final int LOWER_IMG_MAX_WIDTH = 800;
    public static final int LOWER_IMG_MAX_HEIGHT = 600;
    public static final int LOWER_IMG_QUALITY = 70;

    public static final int DEFAULT_THUMB_WIDTH = 225;
    public static final int DEFAULT_THUMB_HEIGHT = 225;

    private static final String APP_FACING_LENS = "android.intent.extras.LENS_FACING_FRONT";
    private static final String APP_FACING_CAMERA = "android.intent.extras.CAMERA_FACING";

    private File imgFile;
    private String imgTitle;
    private Bitmap imgBitmap;
    private String imgUri;

    private final Context context;
    private final ImageView imgView;

    public ImageComponent(Context context, ImageView imgView) {
        this.context = context;
        this.imgView = imgView;
    }

    public ImageComponent clear() {
        nullifyMemory();
        imgView.setImageBitmap(null);

        return this;
    }

    public ImageComponent clear(int drawableResource) {
        nullifyMemory();
        imgView.setImageResource(drawableResource);

        return this;
    }

    public void nullifyMemory() {
        imgFile = null;
        imgTitle = null;
        imgBitmap = null;
        imgUri = null;
    }

    public ImageView getImageView() {
        return imgView;
    }

    public File getImgFile() {
        return imgFile;
    }

    public ImageComponent setImgFile(File imgFile) {
        this.imgFile = imgFile;
        return this;
    }

    public Bitmap getImgBitmap() {
        return imgBitmap;
    }

    public ImageComponent setImgBitmap(Bitmap imgBitmap) {
        this.imgBitmap = imgBitmap;
        return this;
    }

    public String getImgUri() {
        return imgUri;
    }

    public ImageComponent setImgUri(String imgUri) {
        this.imgUri = imgUri;
        return this;
    }

    public String getImageTitle() {
        String timeStamp = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US).format(new Date());
        return "img_" + timeStamp;
    }

    public ImageComponent createImageFile() throws IOException {
        File storageDir = Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES));

        imgTitle = getImageTitle();

        if (!storageDir.exists() && !storageDir.mkdir()) {
            throw  new IOException(context.getString(R.string.img_dir_not_found));
        }

        imgFile = File.createTempFile(imgTitle, ".jpeg", storageDir);

        return this;
    }

    public String getBitmapBase64() {
        return ImageComponent.getBitmapBase64(imgBitmap, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public String getBitmapBase64(int width, int height, int quality) {
        return ImageComponent.getBitmapBase64(imgBitmap, width, height, quality);
    }

    public static String getBitmapBase64(Bitmap imgBitmap) {
        return ImageComponent.getBitmapBase64(imgBitmap, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public static String getBitmapBase64(Bitmap bitmap, int width, int height, int quality) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ImageHelper.toBase64(bitmap, Math.min(width, LOWER_IMG_MAX_WIDTH), Math.min(height, LOWER_IMG_MAX_HEIGHT), Math.min(quality, LOWER_IMG_QUALITY));
        }

        return ImageHelper.toBase64(bitmap, width, height, quality);
    }

    public String getFileBase64() {
        return ImageComponent.getFileBase64(context, imgFile, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public String getFileBase64(int width, int height, int quality) {
        return ImageComponent.getFileBase64(context, imgFile, width, height, quality);
    }

    public static String getFileBase64(Context context, @NonNull File file) {
        return ImageComponent.getFileBase64(context, file, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public static String getFileBase64(Context context, @NonNull File file, int width, int height, int quality) {
        return ImageHelper.toBase64(context, file, width, height, quality);
    }

    public Bitmap getBitmapFromUri() {
        return ImageComponent.getBitmapFromUri(context, imgUri);
    }

    public static Bitmap getBitmapFromUri(Context context, String imgUri) {
        Bitmap image = null;
        Uri uri = Uri.parse(imgUri);

        if (uri.getPath() != null) {
            image = getResizedBitmap(context, uri);
        }

        return image;
    }

    public String getUriBase64() throws IOException {
        return ImageComponent.getUriBase64(context, imgUri);
    }

    public static String getUriBase64(Context context, String UriString) throws IOException {
        String base64Image = null;

        Uri uri = Uri.parse(UriString);

        if (uri.getPath() != null) {
            Bitmap image = ImageHelper.getImageBitmap(context, uri);
            base64Image = ImageComponent.getBitmapBase64(image);
        }

        return base64Image;
    }

    public static Bitmap getBase64ToBitmap(String base64String) {
        return ImageHelper.toBitmap(base64String);
    }

    public ImageComponent createResizedBitmap(@Nullable Uri uriFile) {
        return createResizedBitmap(uriFile, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public ImageComponent createResizedBitmap(@Nullable Uri uriFile, int imgMaxWidth, int imgMaxHeight, int quality) {
        nullifyMemory();

        if (uriFile != null) {
            if (imgTitle == null) {
                imgTitle = getImageTitle();
            }

            ImageHelper.Image image = ImageHelper.resizeImage(context, uriFile, imgTitle, imgMaxWidth, imgMaxHeight, quality);

            imgBitmap = image.bitmap;
            imgUri = image.uri;
        }

        return this;
    }

    public static Bitmap getResizedBitmap(@NonNull Context context, @Nullable Uri uriFile) {
        return getResizedBitmap(context, uriFile, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public static Bitmap getResizedBitmap(Context context, @Nullable Uri uriFile, int imgMaxWidth, int imgMaxHeight, int quality) {
        Bitmap bitmap = null;

        if (uriFile != null) {
            String timeStamp = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US).format(new Date());
            String title = "img_" + timeStamp;

            bitmap = ImageHelper.resizeImage(context, uriFile, title, imgMaxWidth, imgMaxHeight, quality).bitmap;
        }

        return bitmap;
    }

    public ImageComponent createResizedBitmapFromFile() {
        imgBitmap = ImageComponent.createResizedBitmapFromFile(context, imgFile, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
        return this;
    }

    public ImageComponent createResizedBitmapFromFile(int maxWidth, int maxHeight, int quality) {
        imgBitmap = ImageComponent.createResizedBitmapFromFile(context, imgFile, maxWidth, maxHeight, quality);
        return this;
    }

    public static Bitmap createResizedBitmapFromFile(Context context, File file) {
        return ImageComponent.createResizedBitmapFromFile(context, file, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public static Bitmap createResizedBitmapFromFile(Context context, File file, int maxWidth, int maxHeight, int quality) {
        Uri fileUri = Uri.fromFile(file);
        return ImageHelper.resizeImage(context, fileUri, maxWidth, maxHeight, quality).bitmap;
    }

    public static Bitmap createResizedBitmap(Context context, Uri fileUri) {
        return ImageHelper.resizeImage(context, fileUri, DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY).bitmap;
    }

    public static Bitmap getThumbBitmap(String base64String) {
        return ImageComponent.getThumbBitmap(base64String, DEFAULT_THUMB_WIDTH, DEFAULT_THUMB_HEIGHT);
    }

    public static Bitmap getThumbBitmap(String base64String, int thumbWidth, int thumbHeight) {
        Bitmap thumbBitmap = ImageComponent.getBase64ToBitmap(base64String);
        return ImageHelper.scaleDown(thumbBitmap, thumbWidth, thumbHeight);
    }

    public static Bitmap getThumbFromVideo(String file) {
        return ImageComponent.getThumbFromVideo(file, DEFAULT_THUMB_WIDTH, DEFAULT_THUMB_HEIGHT);
    }

    public static Bitmap getThumbFromVideo(String file, int thumbWidth, int thumbHeight) {
        return ImageHelper.getThumbFromVideo(file, thumbWidth, thumbHeight);
    }

    public static Bitmap getBitmapFromRes(Resources res, int resId) {
        return BitmapFactory.decodeResource(res, resId);
    }

    public ImageComponent storeImg() {
        return storeImg(DEFAULT_IMG_MAX_WIDTH, DEFAULT_IMG_MAX_HEIGHT, DEFAULT_IMG_QUALITY);
    }

    public ImageComponent storeImg(int maxWidth, int maxHeight) {
        return storeImg(maxWidth, maxHeight, DEFAULT_IMG_QUALITY);
    }

    public ImageComponent storeImg(int maxWidth, int maxHeight, int quality) {
        Uri uri = Uri.fromFile(getImgFile());

        ImageHelper.Image image;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            image = ImageHelper.createBitmap(context, uri, imgTitle, Math.min(maxWidth, LOWER_IMG_MAX_WIDTH), Math.min(maxHeight, LOWER_IMG_MAX_HEIGHT), Math.min(quality, LOWER_IMG_QUALITY));
        } else {
            image = ImageHelper.createBitmap(context, uri, imgTitle, maxWidth, maxHeight, quality);
        }

        imgBitmap = image.bitmap;
        imgUri = image.uri;

        return this;
    }

    public void insertImage() {
        /* load Uri on lib or direct load Bitmap on view */
        if (imgUri != null && imgUri.length() > 0) {
            Uri uri = Uri.parse(imgUri);

            Glide.with(context)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .fitCenter()
                    .into(imgView);

        } else if (imgBitmap != null) {
            imgView.setImageBitmap(imgBitmap);
        }
    }

    public void insertInto(Context context, ImageView viewTarget, Uri imgUri) {
        Glide.with(context)
                .load(imgUri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .fitCenter()
                .into(viewTarget);
    }

    public Uri getUriForFile() {
        return ImageComponent.getUriForFile(context, imgFile);
    }

    public static Uri getUriForFile(Context context, File file) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        }

        return Uri.fromFile(file);
    }

    public static Bitmap scaleDown(Bitmap bitmap, int maxWidth, int maxHeight) {
        return ImageHelper.scaleDown(bitmap, maxWidth, maxHeight);
    }

    public Intent configCameraIntent(boolean facingFront) {
        return ImageComponent.configCameraIntent(getUriForFile(), facingFront);
    }

    public static Intent configCameraIntent(Uri uri, boolean facingFront) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        /* validate camera facing orientation (front/back) */
        ImageComponent.setCameraPosition(intent, facingFront);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

        return intent;
    }

    public static Intent configGalleryIntent(Context context) {
        Intent intent = new Intent();

        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        return Intent.createChooser(intent, context.getString(R.string.select_img));
    }

    public static Intent configMultiGalleryIntent(Context context) {
        Intent intent = new Intent();

        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        return Intent.createChooser(intent, context.getString(R.string.select_img));
    }

    public static void setCameraPosition(Intent intent, boolean facingFront) {
        if (facingFront) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                intent.putExtra(ImageComponent.APP_FACING_LENS, 1);
            } else {
                intent.putExtra(ImageComponent.APP_FACING_CAMERA, 1);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                intent.putExtra(ImageComponent.APP_FACING_LENS, 0);
            } else {
                intent.putExtra(ImageComponent.APP_FACING_CAMERA, 0);
            }
        }
    }
}
