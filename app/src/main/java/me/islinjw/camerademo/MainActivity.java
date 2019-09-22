package me.islinjw.camerademo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.view.TextureView;

public class MainActivity extends AppCompatActivity {
    private static int REQUEST_CODE = 0x1;

    private CameraCapturer mCameraCapturer = new CameraCapturer();

    private HandlerThread mRenderThread = new HandlerThread("render");
    private Handler mRenderHandler;

    private TextureView mPreview;
    private boolean mIsRunning;
    private SurfaceTexture mCameraTexture;

    private float[] mTransformMatrix = new float[16];

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            mCameraTexture.updateTexImage();
            mCameraTexture.getTransformMatrix(mTransformMatrix);

            mGLRender.render(mTransformMatrix);
        }
    };

    private GLRender mGLRender = new GLRender();

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mRenderThread.start();
        mRenderHandler = new Handler(mRenderThread.getLooper());

        mPreview = findViewById(R.id.preview);
        mPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
                mRenderHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        initRender(surface, width, height);
                        checkPermissionsAndOpenCamera();
                    }
                });
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
    }

    private void initRender(SurfaceTexture surface, int width, int height) {
        mGLRender.init(this, surface, width, height);
        mCameraTexture = new SurfaceTexture(mGLRender.getTexture());
    }

    @SuppressLint("NewApi")
    private void checkPermissionsAndOpenCamera() {
        int check = PermissionChecker.checkSelfPermission(
            MainActivity.this, Manifest.permission.CAMERA);
        if (check == PermissionChecker.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions[0] == Manifest.permission.CAMERA
            && grantResults[0] == PermissionChecker.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCameraTexture != null && !mIsRunning) {
            openCamera();
        }
    }

    private void openCamera() {
        mIsRunning = true;
        mCameraCapturer.openCamera(MainActivity.this,
            mCameraTexture,
            CameraCharacteristics.LENS_FACING_BACK,
            mPreview.getWidth(),
            mPreview.getHeight(),
            mCaptureCallback,
            mRenderHandler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mIsRunning) {
            closeCamera();
        }
    }

    private void closeCamera() {
        mIsRunning = false;
        mCameraCapturer.closeCamera();
    }
}
