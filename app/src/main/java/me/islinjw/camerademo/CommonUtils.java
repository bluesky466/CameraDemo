package me.islinjw.camerademo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class CommonUtils {
    public static FloatBuffer toFloatBuffer(float[] data) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length * 4);
        buffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        floatBuffer.put(data);
        floatBuffer.position(0);
        return floatBuffer;
    }

    public static ShortBuffer toShortBuffer(short[] data) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length * 2);
        buffer.order(ByteOrder.nativeOrder());
        ShortBuffer shortBuffer = buffer.asShortBuffer();
        shortBuffer.put(data);
        shortBuffer.position(0);
        return shortBuffer;
    }
}
