package com.beyond.jgit.util;

public class FormatUtils {

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

    public static int dynamicAddTypeAndSize(int type, int typeBitLength, int size, byte[] result, int offset) {
        int sizeLength = 7 - typeBitLength;
        byte sizeFlag = (byte) (0xff >> (typeBitLength+1));
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

    public static int readNextDynamicTypeAndSize(int typeBitLength, byte[] bytes, int offset, int[] result){
        int sizeLength = 7 - typeBitLength;
        byte sizeFlag = (byte) (0xff >> (typeBitLength+1));
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

    public static int readNextDynamicSimpleTypeAndSize(byte[] bytes, int offset, int[] result){
        return readNextDynamicTypeAndSize(1, bytes, offset, result);
    }

}
