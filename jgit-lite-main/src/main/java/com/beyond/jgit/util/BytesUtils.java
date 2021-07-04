package com.beyond.jgit.util;

public class BytesUtils {

    public static boolean equals(String s, byte[] bytes){
        return s.equals(new String(bytes));
    }

    public static int indexUntil(byte[] bytes, int offset, byte target) {
        for (int i = offset; i < bytes.length; i++) {
            if (bytes[i] == target) {
                return i;
            }
        }
        return -1;
    }


    public static byte[] collectUntil(byte[] bytes, int offset, byte target) {
        int index = indexUntil(bytes, offset, target);
        if (index > 0){
            byte[] newBytes = new byte[index - offset];
            System.arraycopy(bytes, offset, newBytes, 0, newBytes.length);
            return newBytes;
        }
        return new byte[0];
    }

    public static byte[] collectUntil(byte[] bytes, int offset, byte[] targets) {
        int index = 0;
        for (int i = offset; i < bytes.length; i++) {
            boolean found = true;
            int j = i;
            for (byte target : targets) {
                if (bytes[j] != target) {
                    found = false;
                    break;
                }
                j++;
            }
            if (found){
                index = i;
                break;
            }
        }
        if (index > 0){
            byte[] newBytes = new byte[index - offset];
            System.arraycopy(bytes, offset, newBytes, 0, newBytes.length);
            return newBytes;
        }
        return new byte[0];
    }

    public static void main(String[] args) {
        byte[] bytes = "djofakdjfoaidnfoaifj".getBytes();
        byte[] bytes1 = collectUntil(bytes, 6, "aid".getBytes());
        System.out.println(new String(bytes1));
    }

    public static byte[] collectByLength(byte[] bytes, int offset, int len) {
        if (offset + len > bytes.length){
            len = bytes.length - offset;
        }
        if (len == 0){
            return new byte[0];
        }
        byte[] newBytes = new byte[len];
        System.arraycopy(bytes, offset, newBytes, 0, newBytes.length);
        return newBytes;
    }

    public static byte[] collectAfter(byte[] bytes, int offset) {
        byte[] newBytes = new byte[bytes.length - offset];
        System.arraycopy(bytes, offset, newBytes, 0, newBytes.length);
        return newBytes;
    }
}
