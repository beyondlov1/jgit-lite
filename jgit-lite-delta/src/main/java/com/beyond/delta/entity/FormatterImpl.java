package com.beyond.delta.entity;


import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
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
    public byte[] format(List<Delta> deltas) {

        int size = 0;
        for (Delta delta : deltas) {
            size += sizeOne(delta);
        }

        byte[] result = new byte[size];
        format(deltas, result, 0);

        return result;
    }

    @Override
    public void format(List<Delta> deltas, byte[] result, int offset) {
        for (Delta delta : deltas) {
            offset = formatOneTo(delta, result, offset);
        }
    }

    private int sizeOne(Delta delta) {

        if (delta instanceof CopyRangeDelta) {
            // type+targetOffset+size+originOffset
            int targetOffset = delta.getTargetRange().getStart();
            int size = delta.getTargetRange().length();
            int originOffset = ((CopyRangeDelta) delta).getOriginRange().getStart();

            return 1 + byteSize(targetOffset) + byteSize(size) + byteSize(originOffset);
        }

        if (delta instanceof InsertLiterDelta) {
            // type+targetOffset+size+liter
            int targetOffset = delta.getTargetRange().getStart();
            int size = delta.getTargetRange().length();
            int literSize = delta.getTargetRange().length();
            return 1 + byteSize(targetOffset) + byteSize(size) + literSize;
        }

        throw new RuntimeException("未知类型");
    }

    private static int byteSize(int value) {
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
        if (value <= Long.valueOf("11111111111111111111111111111111111", 2)) {
            return 5;
        }
        throw new RuntimeException("数值过大，暂不支持");
    }

    public int formatOneTo(Delta delta, byte[] result, int offset) {

        if (delta instanceof CopyRangeDelta) {
            // type+targetOffset+size+originOffset
            result[offset] = 0;
            offset = offset + 1;

            int targetOffset = delta.getTargetRange().getStart();
            addValue(targetOffset, result, offset);
            offset = offset + byteSize(targetOffset);

            int size = delta.getTargetRange().length();
            addValue(size, result, offset);
            offset = offset + byteSize(size);

            int originOffset = ((CopyRangeDelta) delta).getOriginRange().getStart();
            addValue(originOffset, result, offset);
            offset = offset + byteSize(originOffset);

            return offset;
        }

        if (delta instanceof InsertLiterDelta) {
            // type+targetOffset+size+liter
            int literSize = delta.getTargetRange().length();
            result[offset] = 1;
            offset = offset + 1;

            int targetOffset = delta.getTargetRange().getStart();
            addValue(targetOffset, result, offset);
            offset = offset + byteSize(targetOffset);

            int size = delta.getTargetRange().length();
            addValue(size, result, offset);
            offset = offset + byteSize(size);

            byte[] literal = ((InsertLiterDelta) delta).getLiteral();
            System.arraycopy(literal, 0, result, offset, size);
            offset = offset + literSize;

            return offset;
        }

        throw new RuntimeException("未知类型");
    }

    private static void addValue(int n, byte[] result, int offset) {
        int byteSize = byteSize(n);
        int tmp = n;
        for (int i = 0; i < byteSize; i++) {
            if (i == byteSize - 1){
                result[offset+i] = (byte) (tmp & 0x7f) ;
            }else {
                result[offset+i] = (byte) (tmp & 0x7f | 0x80);
            }
            tmp = tmp >> 7;
        }
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

        while (offset < len) {
            int nextOffset = offset;
            byte type = readNextByte(deltasBytes, nextOffset);
            nextOffset += 1;

            int targetOffset = readValue(deltasBytes, nextOffset);
            nextOffset += byteSize(targetOffset);

            int size = readValue(deltasBytes, nextOffset);
            nextOffset += byteSize(size);

            if (type == 0) {
                int originOffset = readValue(deltasBytes, nextOffset);
                nextOffset += byteSize(originOffset);

                CopyRangeDelta copyRangeDelta = new CopyRangeDelta(new Range(originOffset, originOffset + size), new Range(targetOffset, targetOffset+size));
                result.add(copyRangeDelta);
            }

            if (type == 1) {
                byte[] literal = new byte[size];
                System.arraycopy(deltasBytes, nextOffset, literal, 0, size);
                nextOffset += size;
                InsertLiterDelta insertLiterDelta = new InsertLiterDelta(new Range(targetOffset, targetOffset+ size), literal);
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


    private static int readValue(byte[] bytes, int offset) {
        int len = 0;
        for (int i = offset; ; i++) {
            byte b = readNextByte(bytes, i);
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
            res = ((bytes[offset + i] & 0x7f) << (i * 7)) + res  ;
        }
        return res;
    }


    public static void main(String[] args) {
        int i = 956564654;
        byte[] bytes = new byte[byteSize(i)];
        addValue(i, bytes, 0);
        System.out.println(Arrays.toString(bytes));

        int i1 = readValue(bytes, 0);
        System.out.println(i1);

        // 11111111 -> 11111110 -> 00000111
        byte i2 = ((~0x00b) << 1) >> 5;
        System.out.println(i2);
        System.out.println((byte)((byte)((~0x00) << 1)));
        Byte aByte = Byte.valueOf("01111111", 2);
        System.out.println(aByte);


        System.out.println(Integer.toBinaryString((byte)((byte)(~0x00 & 0xff) << 1) >> 5));
        // 01111111  11111110 00000111
        System.out.println((0x7f & 0xff));
        System.out.println(Math.pow(2,8));
        System.out.println(((0x7f & 0xff) << 1) >> 5);


        byte[] bytes1 = new byte[byteSize(956564654)];
        System.out.println(byteSize(956564654));
        addValue(956564654, bytes1, 0);
        System.out.println(Arrays.toString(bytes1));
        String s = Integer.toBinaryString(956564654);
        System.out.println(s);
        System.out.println( (byte)(Byte.valueOf(StringUtils.substring(s, 30-7, 30),2) |0x80));
        System.out.println((byte)( Byte.valueOf(StringUtils.substring(s, 30-7*2, 30-7),2)|0x80));
        System.out.println( (byte)(Byte.valueOf(StringUtils.substring(s, 30-7*3, 30-7*2),2)|0x80));
        System.out.println( (byte)( Byte.valueOf(StringUtils.substring(s, 30-7*4, 30-7*3),2)|0x80));
        System.out.println( (byte)(Byte.valueOf(StringUtils.substring(s, 0, 30-7*4),2)));

        int i3 = readValue(bytes1, 0);
        System.out.println(i3);

    }


}
