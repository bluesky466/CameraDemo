package me.islinjw.camerademo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.TextureView;

public class MainActivity extends AppCompatActivity {
    private static int REQUEST_CODE = 0x1;

    private CameraCapturer mCameraCapturer = new CameraCapturer();

    private HandlerThread mRenderThread = new HandlerThread("render");
    private Handler mRenderHandler;

    private TextureView mPreview;
    private boolean mIsRunning;
    private SurfaceTexture mSurfaceTexture;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mRenderThread.start();
        mRenderHandler = new Handler(mRenderThread.getLooper());

        mPreview = findViewById(R.id.preview);
        mPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @SuppressLint("NewApi")
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mSurfaceTexture = surface;

                int check = PermissionChecker.checkSelfPermission(
                        MainActivity.this, Manifest.permission.CAMERA);
                if (check == PermissionChecker.PERMISSION_GRANTED) {
                    openCamera();
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE);
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

        int check = PermissionChecker.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (PermissionChecker.PERMISSION_DENIED == check) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
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
        if (mSurfaceTexture != null && !mIsRunning) {
            openCamera();
        }
    }

    private void openCamera() {
        mIsRunning = true;
        mCameraCapturer.openCamera(MainActivity.this,
                mSurfaceTexture,
                CameraCharacteristics.LENS_FACING_BACK,
                mPreview.getWidth(),
                mPreview.getHeight(),
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int check = PermissionChecker.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (PermissionChecker.PERMISSION_GRANTED == check) {
                mCameraCapturer.takePhoto();
            }
        }
        return super.onTouchEvent(event);
    }
}
