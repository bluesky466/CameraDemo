package me.islinjw.camerademo;

import android.Manifest;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

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


    private ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            savePhoto(reader);
        }
    };

    private Surface mPreviewSurface;
    private CameraDevice mCameraDevice;

    private HandlerThread mRenderThread = new HandlerThread("render");
    private Handler mRenderHandler;

    private Executor mSavePhotoExecutor = Executors.newSingleThreadExecutor();

    private TextureView mPreview;
    private SurfaceTexture mSurfaceTexture;

    private CameraCaptureSession mCameraCaptureSession;

    private ImageReader mImageReader;

    private int mSensorOrientation;

    private boolean mIsRunning;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mPreview = findViewById(R.id.preview);
        mPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @SuppressLint("NewApi")
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mSurfaceTexture = surface;
                mPreviewSurface = new Surface(surface);
                int check = PermissionChecker.checkSelfPermission(
                        MainActivity.this, Manifest.permission.CAMERA);
                if (check == PermissionChecker.PERMISSION_GRANTED) {
                    openCamera(mSurfaceTexture,
                            CameraCharacteristics.LENS_FACING_BACK,
                            mPreview.getWidth(),
                            mPreview.getHeight());
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 0x1);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        mRenderThread.start();
        mRenderHandler = new Handler(mRenderThread.getLooper());

        int check = PermissionChecker.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (PermissionChecker.PERMISSION_DENIED == check) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0x1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions[0] == Manifest.permission.CAMERA
                && grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
            openCamera(mSurfaceTexture,
                    CameraCharacteristics.LENS_FACING_BACK,
                    mPreview.getWidth(),
                    mPreview.getHeight());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSurfaceTexture != null && !mIsRunning) {
            int check = PermissionChecker.checkSelfPermission(
                    MainActivity.this, Manifest.permission.CAMERA);
            if (check == PermissionChecker.PERMISSION_GRANTED) {
                openCamera(mSurfaceTexture,
                        CameraCharacteristics.LENS_FACING_BACK,
                        mPreview.getWidth(),
                        mPreview.getHeight());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mIsRunning) {
            closeCamera();
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(
            SurfaceTexture preview,
            int facing,
            int width,
            int height) {
        mIsRunning = true;

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

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

                    manager.openCamera(id, mOpenCameraCallback, mRenderHandler);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can not open camera", e);
        }
    }

    private void closeCamera() {
        mIsRunning = false;

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

    private void openCameraSession(CameraDevice camera) {
        mCameraDevice = camera;
        try {
            List<Surface> outputs = Arrays.asList(mPreviewSurface, mImageReader.getSurface());
            camera.createCaptureSession(outputs, mCreateSessionCallback, mRenderHandler);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int check = PermissionChecker.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (PermissionChecker.PERMISSION_GRANTED == check) {
                takePhoto();
            }
        }
        return super.onTouchEvent(event);
    }

    private ImageReader getImageReader(Size size) {
        ImageReader imageReader = ImageReader.newInstance(
                size.getWidth(),
                size.getHeight(),
                ImageFormat.JPEG,
                5);
        imageReader.setOnImageAvailableListener(mOnImageAvailableListener, mRenderHandler);
        return imageReader;
    }

    private void takePhoto() {
        try {
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(mPreviewSurface);
            builder.addTarget(mImageReader.getSurface());
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            builder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            mCameraCaptureSession.capture(builder.build(), null, null);
        } catch (CameraAccessException e) {
            Log.d(TAG, "takePhoto failed", e);
        }
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private void savePhoto(ImageReader reader) {
        final Image image = reader.acquireNextImage();
        if (image == null) {
            return;
        }
        long time = System.currentTimeMillis();
        final String filename = "IMG_" + time + ".jpeg";
        Toast.makeText(this, filename, Toast.LENGTH_SHORT).show();

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
            }
        });
    }
}
