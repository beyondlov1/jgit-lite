package com.beyond.jgit.util;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public abstract class FormatUtils {

    public static byte[] checksum(byte[] bytes, int from, int to) throws IOException {
        return DigestUtils.sha1(new ByteArrayInputStream(bytes, from, to));
    }

    public static int writeIntTo(int n, byte[] result, int offset) {
        result[offset + 3] = (byte) (n & 0xff);
        result[offset + 2] = (byte) (n >> 8 & 0xff);
        result[offset + 1] = (byte) (n >> 16 & 0xff);
        result[offset] = (byte) (n >> 24 & 0xff);
        return offset + 4;
    }


    public static int writeShortTo(short n, byte[] result, int offset) {
        result[offset + 1] = (byte) (n & 0xff);
        result[offset] = (byte) (n >> 8 & 0xff);
        return offset + 2;
    }


    public static int writeBytesTo(byte[] target, byte[] result, int offset) {
        System.arraycopy(target, 0, result, offset, target.length);
        return offset + target.length;
    }

    public static int dynamicAddInt(int n, byte[] result, int offset) {
        int byteSize = FormatUtils.dynamicByteSize(n);
        int tmp = n;
        for (int i = 0; i < byteSize; i++) {
            if (i == byteSize - 1) {
                result[offset + i] = (byte) (tmp & 0x7f);
            } else {
                result[offset + i] = (byte) (tmp & 0x7f | 0x80);
            }
            tmp = tmp >> 7;
        }
        return offset + byteSize;
    }

    public static int dynamicByteSize(int value) {
        if (value <= Integer.valueOf("1111111", 2)) {
            return 1;
        }
        if (value <= Integer.valueOf("11111111111111", 2)) {
            return 2;
        }
        if (value <= Integer.valueOf("111111111111111111111", 2)) {
            return 3;
        }
        if (value <= Long.valueOf("1111111111111111111111111111", 2)) {
            return 4;
        }
        return 5;
    }

    public static int dynamicByteSizeOfTypeAndSize(int typeBitLength, int size) {
        if ((size >> (7 - typeBitLength)) == 0) {
            return 1;
        }
        return 1 + dynamicByteSize(size >> (7 - typeBitLength));
    }

    public static int dynamicAddTypeAndSize(int type, int typeBitLength, int size, byte[] result, int offset) {
        int sizeLength = 7 - typeBitLength;
        byte sizeFlag = (byte) (0xff >> (typeBitLength + 1));
        byte firstByte = (byte) (size & sizeFlag | type << sizeLength);
        if (size > sizeFlag) {
            result[offset] = (byte) (firstByte | 0x80);
            return FormatUtils.dynamicAddInt(size >> sizeLength, result, offset + 1);
        } else {
            result[offset] = firstByte;
            return offset + 1;
        }
    }

    public static int dynamicAddSimpleTypeAndSize(int type, int size, byte[] result, int offset) {
        return dynamicAddTypeAndSize(type, 1, size, result, offset);
    }

    public static int readNextDynamicInt(byte[] bytes, int offset) {
        int len = 0;
        for (int i = offset; ; i++) {
            byte b = bytes[i];
            if (b < 0) {
                len++;
            }
            if (b >= 0) {
                len++;
                break;
            }
        }

        int res = 0;
        for (int i = 0; i < len; i++) {
            res = ((bytes[offset + i] & 0x7f) << (i * 7)) + res;
        }
        return res;
    }

    public static int readNextDynamicTypeAndSize(int typeBitLength, byte[] bytes, int offset, int[] result) {
        int sizeLength = 7 - typeBitLength;
        byte sizeFlag = (byte) (0xff >> (typeBitLength + 1));
        int nextOffset = offset;
        byte typeAndSizeByte = bytes[offset];
        nextOffset += 1;
        byte type = (byte) ((typeAndSizeByte & 0x7f) >> sizeLength);
        int msb = (typeAndSizeByte & 0x80) >> 7;
        int size = typeAndSizeByte & sizeFlag;
        if (msb == 1) {
            int sizePartHigh = FormatUtils.readNextDynamicInt(bytes, nextOffset);
            nextOffset += FormatUtils.dynamicByteSize(sizePartHigh);
            size = size + (sizePartHigh << sizeLength);
        }

        result[0] = msb;
        result[1] = type;
        result[2] = size;

        return nextOffset;
    }

    public static int readNextDynamicSimpleTypeAndSize(byte[] bytes, int offset, int[] result) {
        return readNextDynamicTypeAndSize(1, bytes, offset, result);
    }

    public static byte[] readNextBytes(byte[] bytes, int offset, int len) {
        byte[] result = new byte[len];
        System.arraycopy(bytes, offset, result, 0, len);
        return result;
    }

    public static int readNextUnsignedByte(byte[] bytes, int offset) {
        return bytes[offset]  & 0xff ;
    }


    public static int readNextInt(byte[] bytes, int offset) {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            res += (bytes[offset + i] & 0xff) << ((3 - i) * 8);
        }
        return res;
    }


    public static int readNextShort(byte[] bytes, int offset) {
        int res = 0;
        for (int i = 0; i < 2; i++) {
            res += (bytes[offset + i] & 0xff) << ((1 - i) * 8);
        }
        return res;
    }

}
