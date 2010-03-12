/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.hawtdispatch.example.buffer;

import java.io.IOException;

/**
 * Used to write and read primitives to and from a Buffer.
 */
public final class BufferEditor {

    private BufferEditor() {    
    }
    
    public static byte[] toByteArray(Buffer buffer) {
        if (buffer.offset == 0 && buffer.length == buffer.data.length) {
            return buffer.data;
        }

        byte rc[] = new byte[buffer.length];
        System.arraycopy(buffer.data, buffer.offset, rc, 0, buffer.length);
        return rc;
    }

    private static void spaceNeeded(Buffer buffer, int i) {
        assert buffer.offset + i <= buffer.length;
    }

    public static int remaining(Buffer buffer) {
        return buffer.length - buffer.offset;
    }

    public static int read(Buffer buffer) {
        return buffer.data[buffer.offset++] & 0xff;
    }

    public static void readFully(Buffer buffer, byte[] b) throws IOException {
        readFully(buffer, b, 0, b.length);
    }

    public static void readFully(Buffer buffer, byte[] b, int off, int len) throws IOException {
        spaceNeeded(buffer, len);
        System.arraycopy(buffer.data, buffer.offset, b, off, len);
        buffer.offset += len;
    }

    public static int skipBytes(Buffer buffer, int n) throws IOException {
        int rc = Math.min(n, remaining(buffer));
        buffer.offset += rc;
        return rc;
    }

    public static boolean readBoolean(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 1);
        return read(buffer) != 0;
    }

    public static byte readByte(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 1);
        return (byte)read(buffer);
    }

    public static int readUnsignedByte(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 1);
        return read(buffer);
    }

    public static short readShortBig(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 2);
        return (short)((read(buffer) << 8) + (read(buffer) << 0));
    }

    public static short readShortLittle(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 2);
        return (short)((read(buffer) << 0) + (read(buffer) << 8));
    }

    public static int readUnsignedShortBig(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 2);
        return (read(buffer) << 8) + (read(buffer) << 0);
    }

    public static int readUnsignedShortLittle(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 2);
        return (read(buffer) << 0) + (read(buffer) << 8);
    }

    public static char readCharBig(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 2);
        return (char)((read(buffer) << 8) + (read(buffer) << 0));
    }

    public static char readCharLittle(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 2);
        return (char)((read(buffer) << 0) + (read(buffer) << 8));
    }

    public static int readIntBig(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 4);
        return (read(buffer) << 24) + (read(buffer) << 16) + (read(buffer) << 8) + (read(buffer) << 0);
    }

    public static int readIntLittle(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 4);
        return (read(buffer) << 0) + (read(buffer) << 8) + (read(buffer) << 16) + (read(buffer) << 24);
    }

    public static long readLongBig(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 8);
        return ((long)read(buffer) << 56) + ((long)read(buffer) << 48) + ((long)read(buffer) << 40) + ((long)read(buffer) << 32) + ((long)read(buffer) << 24)
                + ((read(buffer)) << 16) + ((read(buffer)) << 8) + ((read(buffer)) << 0);
    }

    public static long readLongLittle(Buffer buffer) throws IOException {
        spaceNeeded(buffer, 8);
        return (read(buffer) << 0) + (read(buffer) << 8) + (read(buffer) << 16) + ((long)read(buffer) << 24) + ((long)read(buffer) << 32) + ((long)read(buffer) << 40)
                + ((long)read(buffer) << 48) + ((long)read(buffer) << 56);
    }

    public static double readDoubleBig(Buffer buffer) throws IOException {
        return Double.longBitsToDouble(readLongBig(buffer));
    }

    public static double readDoubleLittle(Buffer buffer) throws IOException {
        return Double.longBitsToDouble(readLongLittle(buffer));
    }

    public static float readFloatBig(Buffer buffer) throws IOException {
        return Float.intBitsToFloat(readIntBig(buffer));
    }

    public static float readFloatLittle(Buffer buffer) throws IOException {
        return Float.intBitsToFloat(readIntLittle(buffer));
    }

    public static void write(Buffer buffer, int b) throws IOException {
        spaceNeeded(buffer, 1);
        buffer.data[buffer.offset++] = (byte)b;
    }

    public static void write(Buffer buffer, byte[] b) throws IOException {
        write(buffer, b, 0, b.length);
    }

    public static void write(Buffer buffer, byte[] b, int off, int len) throws IOException {
        spaceNeeded(buffer, len);
        System.arraycopy(b, off, buffer.data, buffer.offset, len);
        buffer.offset += len;
    }

    public static void writeBoolean(Buffer buffer, boolean v) throws IOException {
        spaceNeeded(buffer, 1);
        write(buffer, v ? 1 : 0);
    }

    public static void writeByte(Buffer buffer, int v) throws IOException {
        spaceNeeded(buffer, 1);
        write(buffer, v);
    }

    public static void writeShortBig(Buffer buffer, int v) throws IOException {
        spaceNeeded(buffer, 2);
        write(buffer, (v >>> 8) & 0xFF);
        write(buffer, (v >>> 0) & 0xFF);
    }

    public static void writeShortLittle(Buffer buffer, int v) throws IOException {
        spaceNeeded(buffer, 2);
        write(buffer, (v >>> 0) & 0xFF);
        write(buffer, (v >>> 8) & 0xFF);
    }

    public static void writeCharBig(Buffer buffer, int v) throws IOException {
        spaceNeeded(buffer, 2);
        write(buffer, (v >>> 8) & 0xFF);
        write(buffer, (v >>> 0) & 0xFF);
    }

    public static void writeCharLittle(Buffer buffer, int v) throws IOException {
        spaceNeeded(buffer, 2);
        write(buffer, (v >>> 0) & 0xFF);
        write(buffer, (v >>> 8) & 0xFF);
    }

    public static void writeIntBig(Buffer buffer, int v) throws IOException {
        spaceNeeded(buffer, 4);
        write(buffer, (v >>> 24) & 0xFF);
        write(buffer, (v >>> 16) & 0xFF);
        write(buffer, (v >>> 8) & 0xFF);
        write(buffer, (v >>> 0) & 0xFF);
    }

    public static void writeIntLittle(Buffer buffer, int v) throws IOException {
        spaceNeeded(buffer, 4);
        write(buffer, (v >>> 0) & 0xFF);
        write(buffer, (v >>> 8) & 0xFF);
        write(buffer, (v >>> 16) & 0xFF);
        write(buffer, (v >>> 24) & 0xFF);
    }

    public static void writeLongBig(Buffer buffer, long v) throws IOException {
        spaceNeeded(buffer, 8);
        write(buffer, (int)(v >>> 56) & 0xFF);
        write(buffer, (int)(v >>> 48) & 0xFF);
        write(buffer, (int)(v >>> 40) & 0xFF);
        write(buffer, (int)(v >>> 32) & 0xFF);
        write(buffer, (int)(v >>> 24) & 0xFF);
        write(buffer, (int)(v >>> 16) & 0xFF);
        write(buffer, (int)(v >>> 8) & 0xFF);
        write(buffer, (int)(v >>> 0) & 0xFF);
    }

    public static void writeLongLittle(Buffer buffer, long v) throws IOException {
        spaceNeeded(buffer, 8);
        write(buffer, (int)(v >>> 0) & 0xFF);
        write(buffer, (int)(v >>> 8) & 0xFF);
        write(buffer, (int)(v >>> 16) & 0xFF);
        write(buffer, (int)(v >>> 24) & 0xFF);
        write(buffer, (int)(v >>> 32) & 0xFF);
        write(buffer, (int)(v >>> 40) & 0xFF);
        write(buffer, (int)(v >>> 48) & 0xFF);
        write(buffer, (int)(v >>> 56) & 0xFF);
    }

    public static void writeDoubleBig(Buffer buffer, double v) throws IOException {
        writeLongBig(buffer, Double.doubleToLongBits(v));
    }

    public static void writeDoubleLittle(Buffer buffer, double v) throws IOException {
        writeLongLittle(buffer, Double.doubleToLongBits(v));
    }

    public static void writeFloatBig(Buffer buffer, float v) throws IOException {
        writeIntBig(buffer, Float.floatToIntBits(v));
    }

    public static void writeFloatLittle(Buffer buffer, float v) throws IOException {
        writeIntLittle(buffer, Float.floatToIntBits(v));
    }

    public static void writeRawDoubleBig(Buffer buffer, double v) throws IOException {
        writeLongBig(buffer, Double.doubleToRawLongBits(v));
    }

    public static void writeRawDoubleLittle(Buffer buffer, double v) throws IOException {
        writeLongLittle(buffer, Double.doubleToRawLongBits(v));
    }

    public static void writeRawFloatBig(Buffer buffer, float v) throws IOException {
        writeIntBig(buffer, Float.floatToRawIntBits(v));
    }

    public static void writeRawFloatLittle(Buffer buffer, float v) throws IOException {
        writeIntLittle(buffer, Float.floatToRawIntBits(v));
    }

}
