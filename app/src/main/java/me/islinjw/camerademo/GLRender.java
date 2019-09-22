package me.islinjw.camerademo;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Created by linjiawei on 2019/9/12.
 * e-mail : linjiawei3046@cvte.com
 */
public class GLRender {
    private static final String VERTICES_SHADER = "shader.vert.glsl";
    private static final String FRAGMENT_SHADER = "shader.frag.glsl";

    private static final float[] VERTICES = {
        -1.0f, 1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f,
        1.0f, 1.0f
    };

    private static final short[] ORDERS = {
        0, 1, 2, // 左下角三角形

        2, 3, 0  // 右上角三角形
    };

    private static float[] TEXTURE_COORDS = {
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    };

    //反相
    private static float[] COLOR_MATRIX = {
        -1.0f, 0.0f, 0.0f, 1.0f,
        0.0f, -1.0f, 0.0f, 1.0f,
        0.0f, 0.0f, -1.0f, 1.0f,
        0.0f, 0.0f, 0.0f, 1.0f
    };


//    // 去色
//    private static float[] COLOR_MATRIX = {
//        0.299f, 0.587f, 0.114f, 0.0f,
//        0.299f, 0.587f, 0.114f, 0.0f,
//        0.299f, 0.587f, 0.114f, 0.0f,
//        0.0f, 0.0f, 0.0f, 1.0f,
//    };

//    // 怀旧
//    private static float[] COLOR_MATRIX = {
//        0.393f, 0.769f, 0.189f, 0.0f,
//        0.349f, 0.686f, 0.168f, 0.0f,
//        0.272f, 0.534f, 0.131f, 0.0f,
//        0.0f, 0.0f, 0.0f, 1.0f
//    };

//    //原图
//    private static float[] COLOR_MATRIX = {
//        1.0f, 0.0f, 0.0f, 0.0f,
//        0.0f, 1.0f, 0.0f, 0.0f,
//        0.0f, 0.0f, 1.0f, 0.0f,
//        0.0f, 0.0f, 0.0f, 1.0f
//    };

    private EGLDisplay mEGLDisplay;
    private EGLConfig mEGLConfig;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;

    private int mProgram;
    private int mPositionId;
    private int mCoordId;
    private int mColorMatrixId;
    private int mTransformMatrixId;
    private int mGLTextureId;
    private int mTexPreviewId;

    private FloatBuffer mVertices;
    private FloatBuffer mCoords;
    private ShortBuffer mOrder;

    public void init(Context context, SurfaceTexture surface, int width, int height) {
        initEGL(surface);
        initOpenGL(context, width, height);
    }

    private void initOpenGL(Context context, int width, int height) {
        GLES20.glClearColor(0, 0, 0, 1.0f);
        GLES20.glViewport(0, 0, width, height);

        AssetManager asset = context.getAssets();
        try {
            mProgram = createProgram(asset.open(VERTICES_SHADER), asset.open(FRAGMENT_SHADER));
        } catch (IOException e) {
            throw new RuntimeException("can't open shader", e);
        }
        GLES20.glUseProgram(mProgram);

        mOrder = CommonUtils.toShortBuffer(ORDERS);

        mPositionId = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mVertices = CommonUtils.toFloatBuffer(VERTICES);
        GLES20.glVertexAttribPointer(mPositionId, 2, GLES20.GL_FLOAT, false, 0, mVertices);
        GLES20.glEnableVertexAttribArray(mPositionId);

        mCoordId = GLES20.glGetAttribLocation(mProgram, "vCoord");
        mCoords = CommonUtils.toFloatBuffer(TEXTURE_COORDS);
        GLES20.glVertexAttribPointer(mCoordId, 2, GLES20.GL_FLOAT, false, 0, mCoords);
        GLES20.glEnableVertexAttribArray(mCoordId);

        mColorMatrixId = GLES20.glGetUniformLocation(mProgram, "uColorMatrix");
        GLES20.glUniformMatrix4fv(mColorMatrixId, 1, true, COLOR_MATRIX, 0);

        mTexPreviewId = GLES20.glGetUniformLocation(mProgram, "texPreview");

        mTransformMatrixId = GLES20.glGetUniformLocation(mProgram, "matTransform");
    }

    public void render(float[] matrix) {
        GLES20.glUniformMatrix4fv(mTransformMatrixId, 1, false, matrix, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_SAMPLER_EXTERNAL_OES, mGLTextureId);
        GLES20.glUniform1i(mTexPreviewId, 0);

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, ORDERS.length, GLES20.GL_UNSIGNED_SHORT, mOrder);
        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }

    private void initEGL(SurfaceTexture surface) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);

        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("can't get eglGetDisplay");
        }

        if (!EGL14.eglInitialize(mEGLDisplay, null, 0, null, 0)) {
            throw new RuntimeException("eglInitialize failed");
        }

        mEGLConfig = chooseEglConfig(mEGLDisplay);
        mEGLContext = createEglContext(mEGLDisplay, mEGLConfig);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext failed");
        }

        mEGLSurface = createEGLSurface(new Surface(surface));
        makeCurrent(mEGLSurface);
    }

    public int getTexture() {
        if (mGLTextureId == -1) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mGLTextureId = textures[0];
        }

        return mGLTextureId;
    }

    private EGLConfig chooseEglConfig(EGLDisplay display) {
        int[] attribList = {
            EGL14.EGL_BUFFER_SIZE, 32,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(
            display,
            attribList,
            0,
            configs,
            0,
            configs.length,
            numConfigs,
            0)) {
            throw new RuntimeException("eglChooseConfig failed");
        }
        return configs[0];
    }

    private EGLContext createEglContext(EGLDisplay display, EGLConfig config) {
        int[] contextList = {
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        };
        return EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            contextList,
            0);
    }

    public EGLSurface createEGLSurface(Surface surface) {
        int[] attribList = {
            EGL14.EGL_NONE
        };
        return EGL14.eglCreateWindowSurface(
            mEGLDisplay,
            mEGLConfig,
            surface,
            attribList,
            0);
    }

    public void makeCurrent(EGLSurface eglSurface) {
        EGL14.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext);
    }

    public int createProgram(InputStream vShaderSource, InputStream fShaderSource) {
        // 创建渲染程序
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, loadShader(GLES20.GL_VERTEX_SHADER, vShaderSource));
        GLES20.glAttachShader(program, loadShader(GLES20.GL_FRAGMENT_SHADER, fShaderSource));
        GLES20.glLinkProgram(program);

        // 检查链接是否出现异常
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("link program failed : " + log);
        }
        return program;
    }

    public int loadShader(int shaderType, InputStream source) {
        // 读取着色器代码
        String sourceStr;
        try {
            sourceStr = readStringFromStream(source);
        } catch (IOException e) {
            throw new RuntimeException("read shaderType " + shaderType + " source failed", e);
        }

        // 创建着色器并且编译
        int shader = GLES20.glCreateShader(shaderType); // 创建着色器程序
        GLES20.glShaderSource(shader, sourceStr); // 加载着色器源码
        GLES20.glCompileShader(shader); // 编译着色器程序

        // 检查编译是否出现异常
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("create shaderType " + shaderType + " failed : " + log);
        }
        return shader;
    }

    public String readStringFromStream(InputStream input) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String line = reader.readLine();
        while (line != null) {
            builder.append(line)
                .append("\n");
            line = reader.readLine();
        }
        return builder.toString();
    }

    public void deleteProgram(int program) {
        GLES20.glDeleteProgram(program);
    }
}
