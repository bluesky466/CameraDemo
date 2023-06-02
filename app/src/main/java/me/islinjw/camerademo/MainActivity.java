package me.islinjw.camerademo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaRecorder;
import android.opengl.EGLSurface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE = 0x1;

    private static final int VIDEO_BIT_RATE = 1024 * 1024 * 1024;
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int AUDIO_BIT_RATE = 44800;

    private CameraCapturer mCameraCapturer = new CameraCapturer();

    private HandlerThread mRenderThread = new HandlerThread("render");
    private Handler mRenderHandler;

    private TextureView mPreview;
    private SurfaceTexture mPreviewTexture;
    private SurfaceTexture mCameraTexture;
    private boolean mIsRunning;

    private EGLSurface mRecordSurface;

    private float[] mTransformMatrix = new float[16];

    private MediaRecorder mMediaRecorder;
    private File mLastVideo;

    private TextView mTips;

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            mCameraTexture.updateTexImage();
            mCameraTexture.getTransformMatrix(mTransformMatrix);

            mGLRender.render(mTransformMatrix, mGLRender.getDefaultEGLSurface());
            if (mRecordSurface != null) {
                mGLRender.render(mTransformMatrix, mRecordSurface);
                mGLRender.setPresentationTime(mRecordSurface, mPreviewTexture.getTimestamp());
            }
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

        mTips = findViewById(R.id.tips);
        mPreview = findViewById(R.id.preview);
        mPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
                mPreviewTexture = surface;

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
        if (permissions.length > 1
            && permissions[0] == Manifest.permission.CAMERA
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
        if (mMediaRecorder != null) {
            stopRecord();
        }
    }

    private void closeCamera() {
        mIsRunning = false;
        mCameraCapturer.closeCamera();
    }


    private EGLSurface startRecord(String filename) {
        mLastVideo = new File(Environment.getExternalStorageDirectory(), filename);

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mLastVideo.getPath());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(VIDEO_BIT_RATE);
        mMediaRecorder.setVideoSize(mPreview.getWidth(), mPreview.getHeight());
        mMediaRecorder.setVideoFrameRate(VIDEO_FRAME_RATE);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioEncodingBitRate(AUDIO_BIT_RATE);
        mMediaRecorder.setOrientationHint(0);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Toast.makeText(this, "failed to prepare MediaRecorder", Toast.LENGTH_LONG)
                .show();
        }

        mMediaRecorder.start();
        mTips.setText(R.string.stoprecord);
        return mGLRender.createEGLSurface(mMediaRecorder.getSurface());
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (mMediaRecorder == null) {
                if (!grantedRecordPermission()) {
                    return false;
                }
                mRecordSurface = startRecord(genFileName());
                Toast.makeText(this, "start record", Toast.LENGTH_SHORT)
                    .show();
            } else {
                stopRecord();
                String tips = "save video to " + mLastVideo.getAbsolutePath();
                Toast.makeText(this, tips, Toast.LENGTH_SHORT)
                    .show();
            }
        }

        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean grantedRecordPermission() {
        List<String> permissions = new ArrayList<>();
        int audioPermission = PermissionChecker.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO);
        if (audioPermission == PermissionChecker.PERMISSION_DENIED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        int audioWrite = PermissionChecker.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (audioWrite == PermissionChecker.PERMISSION_DENIED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissions.isEmpty()) {
            return true;
        }

        requestPermissions(permissions.toArray(new String[permissions.size()]), REQUEST_CODE);

        return false;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkMediaRecorderPermission() {
        int check = PermissionChecker.checkSelfPermission(
            MainActivity.this, Manifest.permission.RECORD_AUDIO);
        if (check == PermissionChecker.PERMISSION_DENIED) {
            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_CODE);
            return false;
        }
        check = PermissionChecker.checkSelfPermission(
            MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (check == PermissionChecker.PERMISSION_DENIED) {
            requestPermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_CODE);
            return false;
        }

        return true;
    }

    private void stopRecord() {
        mMediaRecorder.stop();
        mMediaRecorder.release();
        mMediaRecorder = null;

        mGLRender.destroyEGLSurface(mRecordSurface);
        mRecordSurface = null;
        mTips.setText(R.string.startrecord);
    }

    private String genFileName() {
        return "video_" + System.currentTimeMillis() + ".mp4";
    }

}
