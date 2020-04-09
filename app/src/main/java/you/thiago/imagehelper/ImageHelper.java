package you.thiago.imagehelper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

@SuppressWarnings({"unused", "WeakerAccess"})
public class ImageHelper {
    public static class Image {
        public String uri;
        public Bitmap bitmap;

        public Image() {
            uri = null;
            bitmap = null;
        }

        public Image(Bitmap bitmap, String uri) {
            this.bitmap = bitmap;
            this.uri = uri;
        }
    }

    public static class ImageSize {
        public int width;
        public int height;

        public ImageSize(BitmapFactory.Options bitmapOptions, int maxWidth, int maxHeight) {
            width = bitmapOptions.outWidth;
            height = bitmapOptions.outHeight;

            calculate(maxWidth, maxHeight);
        }

        public ImageSize(Bitmap image, int maxWidth, int maxHeight) {
            width = image.getWidth();
            height = image.getHeight();

            calculate(maxWidth, maxHeight);
        }

        public ImageSize(int _width, int _height, int maxWidth, int maxHeight) {
            width = _width;
            height = _height;

            calculate(maxWidth, maxHeight);
        }

        private void calculate(int maxWidth, int maxHeight) {
            /* calculate ratios */
            float imgRatio = (float) width / (float) height;
            float maxRatio = (float) maxWidth / (float) maxHeight;

            /* re-calculate img size */
            if (height > maxHeight || width > maxWidth) {
                if (imgRatio < maxRatio) {
                    imgRatio = (float) maxHeight / (float) height;
                    width = (int) (imgRatio * width);
                    height = maxHeight;
                } else if (imgRatio > maxRatio) {
                    imgRatio = (float) maxWidth / (float) width;
                    height = (int) (imgRatio * height);
                    width = maxWidth;
                } else {
                    height = maxHeight;
                    width = maxWidth;
                }
            }
        }
    }

    public static Bitmap getImageBitmap(Context context, Uri uri) throws IOException {
        Bitmap bitmap;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri));
        } else {
            bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
        }

        return bitmap;
    }

    public static Image createBitmap(Context context, Uri uri, String title, int width, int height, int quality) {
        Image image = new Image();

        FileDescriptor fileDescriptor;
        ParcelFileDescriptor parcelFileDescriptor;

        try {
            parcelFileDescriptor = context.getContentResolver().openFileDescriptor(uri, "r");

            if (parcelFileDescriptor != null) {
                fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                image.bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                parcelFileDescriptor.close();
            }

            image.bitmap = scaleDown(image.bitmap, width, height);

            float orientation = getOrientation(context, uri);
            if (orientation > 0) {
                image.bitmap = rotateImage(image.bitmap, orientation);
            }

            image.uri = insertImage(context, image.bitmap, title, quality);
        } catch (Exception e) {
            Log.e(ImageComponent.class.getSimpleName(), e.getMessage(), e);
        }

        return image;
    }

    public static Image resizeImage(Context context, Uri fileUri, int maxWidth, int maxHeight, int quality) {
        return resizeImage(context, fileUri, null, maxWidth, maxHeight, quality);
    }

    public static Image resizeImage(Context context, Uri fileUri, @Nullable String title, int maxWidth, int maxHeight, int quality) {
        Image image = new Image();

        try {
            /* config BitmapFactory to only read (don't load in memory) */
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inJustDecodeBounds = true;

            try (InputStream configStream = context.getContentResolver().openInputStream(fileUri)) {
                BitmapFactory.decodeStream(configStream, null, bitmapOptions);

                ImageSize imageSize = new ImageSize(bitmapOptions, maxWidth, maxHeight);

                bitmapOptions.inSampleSize = calculateInSampleSize(bitmapOptions, imageSize.width, imageSize.height);
                bitmapOptions.inJustDecodeBounds = false;
                bitmapOptions.inTempStorage = new byte[16 * 1024];

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                    bitmapOptions.inDither = false;
                }
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                    bitmapOptions.inPurgeable = true;
                    bitmapOptions.inInputShareable = true;
                }

                /* complete load bitmap */
                try (InputStream scaleStream = context.getContentResolver().openInputStream(fileUri)) {
                    image.bitmap = BitmapFactory.decodeStream(scaleStream, null, bitmapOptions);
                    image.bitmap = scaleDown(image.bitmap, maxWidth, maxHeight);
                }

                float orientation = getOrientation(context, fileUri);
                if (orientation > 0) {
                    image.bitmap = rotateImage(image.bitmap, orientation);
                }

                if (title != null) {
                    image.uri = insertImage(context, image.bitmap, title, quality);
                } else {
                    image.uri = fileUri.toString();
                }
            }
        } catch (Exception e) {
            Log.e(ImageHelper.class.getSimpleName(), e.getMessage(), e);
        }

        return image;
    }

    private static String insertImage(Context context, Bitmap bitmap, String title, int quality) {
        String fileUri = "";

        if (bitmap != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, title);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final String relativePath = Environment.DIRECTORY_PICTURES + File.separator + "pictures";
                values.put(MediaStore.Images.Media.RELATIVE_PATH, relativePath);
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            ContentResolver resolver = context.getContentResolver();
            Uri uri = null;

            try {
                final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                uri = resolver.insert(contentUri, values);

                if (uri == null) {
                    throw new IOException("Failed to create new MediaStore record.");
                }

                try (OutputStream stream = resolver.openOutputStream(uri)) {
                    if (stream == null) {
                        throw new IOException("Failed to get output stream.");
                    }

                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                        throw new IOException("Failed to save bitmap.");
                    }
                } catch (IOException e) {
                    resolver.delete(uri, null, null);
                    throw e;
                }
            } catch (Exception e) {
                Log.e(ImageComponent.class.getSimpleName(), e.getMessage(), e);
            }

            if (uri != null) {
                fileUri = uri.toString();
            }
        }

        return fileUri;
    }

    public static Bitmap getThumbFromVideo(String file, int maxWidth, int maxHeight) {
        Bitmap thumbBitmap = null;

        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                Size size = new Size(maxWidth, maxHeight);
                thumbBitmap = ThumbnailUtils.createVideoThumbnail(new File(file), size, null);
            } else {
                thumbBitmap = ThumbnailUtils.createVideoThumbnail(file, MediaStore.Images.Thumbnails.MINI_KIND);
            }
        } catch (IOException e) {
            Log.e(ImageHelper.class.getSimpleName(), e.getMessage(), e);
        }

        return scaleDown(thumbBitmap, maxWidth, maxHeight);
    }

    public static String toBase64(Bitmap bitmap, int width, int height, int quality) {
        /* resize img before encode */
        bitmap = ImageHelper.scaleDown(bitmap, width, height);
        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteOutput);

        return Base64.encodeToString(byteOutput.toByteArray(), Base64.DEFAULT);
    }

    public static String toBase64(Context context, @NonNull File file, int width, int height, int quality) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        Uri uriFile = Uri.fromFile(file);

        float orientation = ImageHelper.getOrientation(context, uriFile);
        if (orientation > 0) {
            bitmap = ImageHelper.rotateImage(bitmap, orientation);
        }

        return ImageHelper.toBase64(bitmap, width, height, quality);
    }

    public static Bitmap toBitmap(String base64String) {
        byte[] encodeByte = Base64.decode(base64String, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = Math.min(heightRatio, widthRatio);
        }

        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;

        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    public static Bitmap scaleDown(Bitmap realImage, int imgMaxWidth, int imgMaxHeight) {
        Bitmap bitmap = null;

        if (realImage != null) {
            ImageSize ratio = new ImageSize(realImage, imgMaxWidth, imgMaxHeight);
            bitmap = Bitmap.createScaledBitmap(realImage, ratio.width, ratio.height, false);
        }

        return bitmap;
    }

    public static float getOrientation(Context context, Uri uri) {
        float rotateAngle = 0;

        try {
            ExifInterface ei;

            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    ei = new ExifInterface(Objects.requireNonNull(in));
                }
            } else {
                ei = new ExifInterface(Objects.requireNonNull(uri.getPath()));
            }

            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotateAngle = 90f;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotateAngle = 180f;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotateAngle = 270f;
                    break;
                default:
                    rotateAngle = 0;
                    break;
            }
        } catch (IOException e) {
            Log.e(ImageComponent.class.getSimpleName(), e.getMessage(), e);
        }

        return rotateAngle;
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}