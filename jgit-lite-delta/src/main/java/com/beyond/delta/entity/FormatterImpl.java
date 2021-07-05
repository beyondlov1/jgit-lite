package com.beyond.delta.entity;


import com.beyond.jgit.util.FormatUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author chenshipeng
 * @date 2021/07/02
 */
@SuppressWarnings("DuplicatedCode")
public class FormatterImpl implements Formatter {


    @Override
    public int size(List<Delta> deltas) {
        int size = 0;
        for (Delta delta : deltas) {
            size += sizeOne(delta);
        }
        return size;
    }

    @Override
    public byte[] format(List<Delta> deltas) {

        int size = size(deltas);

        byte[] result = new byte[size];
        format(deltas, result, 0);

        return result;
    }

    @Override
    public int format(List<Delta> deltas, byte[] result, int offset) {
        for (Delta delta : deltas) {
            offset = formatOneTo(delta, result, offset);
        }
        return offset;
    }

    private int sizeOne(Delta delta) {

        if (delta instanceof CopyRangeDelta) {
            // (type[1]+size[6])+targetOffset+originOffset
            int size = delta.getTargetRange().length();
            int targetOffset = delta.getTargetRange().getStart();
            int originOffset = ((CopyRangeDelta) delta).getOriginRange().getStart();

            return 1 + ((size >> 6) > 0 ? FormatUtils.dynamicByteSize(size >> 6) : 0) + FormatUtils.dynamicByteSize(targetOffset) + FormatUtils.dynamicByteSize(originOffset);
        }

        if (delta instanceof InsertLiterDelta) {
            // (type[1]+size[6])+targetOffset+liter
            int size = delta.getTargetRange().length();
            int targetOffset = delta.getTargetRange().getStart();
            int literSize = delta.getTargetRange().length();
            return 1 + ((size >> 6) > 0 ? FormatUtils.dynamicByteSize(size >> 6) : 0) + FormatUtils.dynamicByteSize(targetOffset) + literSize;
        }

        throw new RuntimeException("未知类型");
    }

    public int formatOneTo(Delta delta, byte[] result, int offset) {

        if (delta instanceof CopyRangeDelta) {
            // type+size+targetOffset+originOffset
            int size = delta.getTargetRange().length();
            offset = FormatUtils.dynamicAddSimpleTypeAndSize(0, size, result, offset);

            int targetOffset = delta.getTargetRange().getStart();
            offset = FormatUtils.dynamicAddInt(targetOffset, result, offset);

            int originOffset = ((CopyRangeDelta) delta).getOriginRange().getStart();
            offset = FormatUtils.dynamicAddInt(originOffset, result, offset);

            return offset;
        }

        if (delta instanceof InsertLiterDelta) {
            // type+size+targetOffset+liter
            int size = delta.getTargetRange().length();
            offset = FormatUtils.dynamicAddSimpleTypeAndSize(1, size, result, offset);

            int targetOffset = delta.getTargetRange().getStart();
            offset = FormatUtils.dynamicAddInt(targetOffset, result, offset);

            byte[] literal = ((InsertLiterDelta) delta).getLiteral();
            System.arraycopy(literal, 0, result, offset, size);
            offset = offset + size;

            return offset;
        }

        throw new RuntimeException("未知类型");
    }


    public void addInt(int n, byte[] result, int offset) {
        result[offset + 3] = (byte) (n & 0xff);
        result[offset + 2] = (byte) (n >> 8 & 0xff);
        result[offset + 1] = (byte) (n >> 16 & 0xff);
        result[offset] = (byte) (n >> 24 & 0xff);
    }

    public void addShort(int n, byte[] result, int offset) {
        result[offset + 1] = (byte) (n & 0xff);
        result[offset] = (byte) (n >> 8 & 0xff);
    }

    @Override
    public List<Delta> parse(byte[] deltasBytes) {
        return parse(deltasBytes, 0, deltasBytes.length);
    }

    @Override
    public List<Delta> parse(byte[] deltasBytes, int offset, int len) {
        List<Delta> result = new ArrayList<>();
        int end = offset + len;
        while (offset < end) {
            int nextOffset = offset;

            int[] typeAndSizeSplit = new int[3];
            nextOffset = FormatUtils.readNextDynamicSimpleTypeAndSize(deltasBytes, nextOffset, typeAndSizeSplit);
            int type = typeAndSizeSplit[1];
            int size = typeAndSizeSplit[2];

            int targetOffset = FormatUtils.readNextDynamicInt(deltasBytes, nextOffset);
            nextOffset += FormatUtils.dynamicByteSize(targetOffset);

            if (type == 0) {
                int originOffset = FormatUtils.readNextDynamicInt(deltasBytes, nextOffset);
                nextOffset += FormatUtils.dynamicByteSize(originOffset);

                CopyRangeDelta copyRangeDelta = new CopyRangeDelta(new Range(originOffset, originOffset + size), new Range(targetOffset, targetOffset + size));
                result.add(copyRangeDelta);
            }

            if (type == 1) {
                byte[] literal = new byte[size];
                System.arraycopy(deltasBytes, nextOffset, literal, 0, size);
                nextOffset += size;
                InsertLiterDelta insertLiterDelta = new InsertLiterDelta(new Range(targetOffset, targetOffset + size), literal);
                result.add(insertLiterDelta);
            }

            offset = nextOffset;
        }

        return result;
    }

    private static byte readNextByte(byte[] bytes, int offset) {
        return bytes[offset];
    }

    private int readNextInt(byte[] bytes, int offset) {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            res += (bytes[offset + i] & 0xff) << ((3 - i) * 8);
        }
        return res;
    }


    private int readNextShort(byte[] bytes, int offset) {
        int res = 0;
        for (int i = 0; i < 2; i++) {
            res += (bytes[offset + i] & 0xff) << ((1 - i) * 8);
        }
        return res;
    }



    public static void main(String[] args) {
        int i = 956564654;
        byte[] bytes = new byte[FormatUtils.dynamicByteSize(i)];
        FormatUtils.dynamicAddInt(i, bytes, 0);
        System.out.println(Arrays.toString(bytes));

        int i1 = FormatUtils.readNextDynamicInt(bytes, 0);
        System.out.println(i1);

        // 11111111 -> 11111110 -> 00000111
        byte i2 = ((~0x00b) << 1) >> 5;
        System.out.println(i2);
        System.out.println((byte) ((byte) ((~0x00) << 1)));
        Byte aByte = Byte.valueOf("01111111", 2);
        System.out.println(aByte);


        System.out.println(Integer.toBinaryString((byte) ((byte) (~0x00 & 0xff) << 1) >> 5));
        // 01111111  11111110 00000111
        System.out.println((0x7f & 0xff));
        System.out.println(Math.pow(2, 8));
        System.out.println(((0x7f & 0xff) << 1) >> 5);


        byte[] bytes1 = new byte[FormatUtils.dynamicByteSize(956564654)];
        System.out.println(FormatUtils.dynamicByteSize(956564654));
        FormatUtils.dynamicAddInt(956564654, bytes1, 0);
        System.out.println(Arrays.toString(bytes1));
        String s = Integer.toBinaryString(956564654);
        System.out.println(s);
        System.out.println((byte) (Byte.valueOf(StringUtils.substring(s, 30 - 7, 30), 2) | 0x80));
        System.out.println((byte) (Byte.valueOf(StringUtils.substring(s, 30 - 7 * 2, 30 - 7), 2) | 0x80));
        System.out.println((byte) (Byte.valueOf(StringUtils.substring(s, 30 - 7 * 3, 30 - 7 * 2), 2) | 0x80));
        System.out.println((byte) (Byte.valueOf(StringUtils.substring(s, 30 - 7 * 4, 30 - 7 * 3), 2) | 0x80));
        System.out.println((byte) (Byte.valueOf(StringUtils.substring(s, 0, 30 - 7 * 4), 2)));

        int i3 = FormatUtils.readNextDynamicInt(bytes1, 0);
        System.out.println(i3);


        byte[] bytes2 = new byte[FormatUtils.dynamicByteSize(56498 >> 4) + 1];
        FormatUtils.dynamicAddSimpleTypeAndSize(0, 56498, bytes2, 0);
        System.out.println(Arrays.toString(bytes2));

        for (byte b : bytes2) {
            System.out.println(Integer.toBinaryString(b));
        }

        int nextOffset = 0;
        byte typeAndSizeByte = readNextByte(bytes2, nextOffset);
        nextOffset += 1;
        byte type = (byte) ((typeAndSizeByte & 0x7f) >> 4);
        int msb = (typeAndSizeByte & 0x80) >> 7;
        int size = typeAndSizeByte & 0x0f;
        if (msb == 1) {
            int sizePartHigh = FormatUtils.readNextDynamicInt(bytes2, nextOffset);
            size = size + sizePartHigh << 4;
            nextOffset += FormatUtils.dynamicByteSize(sizePartHigh);
        }

        System.out.println(type);
        System.out.println(size);

    }


}
