package me.islinjw.camerademo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraCapturer {
    private static final String TAG = "CameraCapturer";

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            savePhoto(reader);
        }
    };

    private CameraDevice.StateCallback mOpenCameraCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    openCameraSession(camera);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                }
            };


    private CameraCaptureSession.StateCallback mCreateSessionCallback =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    requestPreview(session);
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            };

    private Context mContext;

    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private Surface mPreviewSurface;

    private int mSensorOrientation;

    private Handler mHandler;

    private Executor mSavePhotoExecutor = Executors.newSingleThreadExecutor();

    @SuppressLint("MissingPermission")
    public void openCamera(
            Context context,
            SurfaceTexture preview,
            int facing,
            int width,
            int height,
            Handler handler) {
        mContext = context;
        mPreviewSurface = new Surface(preview);
        mHandler = handler;

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics cc = manager.getCameraCharacteristics(id);
                if (cc.get(CameraCharacteristics.LENS_FACING) == facing) {
                    Size[] previewSizes = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(SurfaceTexture.class);
                    Size previewSize = getMostSuitableSize(previewSizes, width, height);
                    preview.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

                    Size[] photoSizes = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(ImageReader.class);
                    mImageReader = getImageReader(getMostSuitableSize(photoSizes, width, height));

                    mSensorOrientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);

                    manager.openCamera(id, mOpenCameraCallback, mHandler);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can not open camera", e);
        }
    }

    public void closeCamera() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private Size getMostSuitableSize(
            Size[] sizes,
            float width,
            float height) {

        float targetRatio = height / width;
        Size result = null;
        for (Size size : sizes) {
            if (result == null || isMoreSuitable(result, size, targetRatio)) {
                result = size;
            }
        }
        return result;
    }

    private boolean isMoreSuitable(Size current, Size target, float targetRatio) {
        if (current == null) {
            return true;
        }
        float dRatioTarget = Math.abs(targetRatio - getRatio(target));
        float dRatioCurrent = Math.abs(targetRatio - getRatio(current));
        return dRatioTarget < dRatioCurrent
                || (dRatioTarget == dRatioCurrent && getArea(target) > getArea(current));
    }

    private int getArea(Size size) {
        return size.getWidth() * size.getHeight();
    }

    private float getRatio(Size size) {
        return ((float) size.getWidth()) / size.getHeight();
    }


    private ImageReader getImageReader(Size size) {
        ImageReader imageReader = ImageReader.newInstance(
                size.getWidth(),
                size.getHeight(),
                ImageFormat.JPEG,
                5);
        imageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);
        return imageReader;
    }

    private void openCameraSession(CameraDevice camera) {
        mCameraDevice = camera;
        try {
            List<Surface> outputs = Arrays.asList(mPreviewSurface, mImageReader.getSurface());
            camera.createCaptureSession(outputs, mCreateSessionCallback, mHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureSession failed", e);
        }
    }

    private void requestPreview(CameraCaptureSession session) {
        if (mCameraDevice == null) {
            return;
        }
        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mPreviewSurface);
            session.setRepeatingRequest(builder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "requestPreview failed", e);
        }
    }

    public void takePhoto() {
        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(mPreviewSurface);
            builder.addTarget(mImageReader.getSurface());
            builder.set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation);
            mCameraCaptureSession.capture(builder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.d(TAG, "takePhoto failed", e);
        }
    }

    private void savePhoto(ImageReader reader) {
        final Image image = reader.acquireNextImage();
        if (image == null) {
            return;
        }
        long time = System.currentTimeMillis();
        final String filename = "IMG_" + time + ".jpeg";
        Toast.makeText(mContext, filename, Toast.LENGTH_SHORT).show();

        mSavePhotoExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File file = new File(Environment.getExternalStorageDirectory(), filename);
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                try {
                    new FileOutputStream(file)
                            .write(bytes);
                } catch (IOException e) {
                    Log.d(TAG, "save photo failed", e);
                }
                image.close();
            }
        });
    }
}
