package com.beyond.jgit.util;

import com.beyond.jgit.object.ObjectEntity;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;


public class ObjectUtils {

    public static final String EMPTY_HASH = "0000000000000000000000000000000000000000";

    public static String sha1hash(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return EMPTY_HASH;
        } else {
            return DigestUtils.sha1Hex(bytes);
        }
    }

    public static String sha1hash(InputStream inputStream) throws IOException {
        return DigestUtils.sha1Hex(inputStream);
    }

    public static String sha1hash(ObjectEntity.Type type, File file) throws IOException {
        try(FileInputStream fileInputStream = new FileInputStream(file);
            ByteArrayInputStream headInputStream = new ByteArrayInputStream((type.name().toLowerCase() + " " + fileInputStream.available() + "\0").getBytes());
            SequenceInputStream sequenceInputStream = new SequenceInputStream(headInputStream, fileInputStream)) {
            return sha1hash(sequenceInputStream);
        }
    }

    public static String sha1hash(ObjectEntity.Type type, InputStream contentInputStream) throws IOException {
        try(ByteArrayInputStream headInputStream = new ByteArrayInputStream((type.name().toLowerCase() + " " + contentInputStream.available() + "\0").getBytes());
            SequenceInputStream sequenceInputStream = new SequenceInputStream(headInputStream, contentInputStream)) {
            return sha1hash(sequenceInputStream);
        }
    }


    public static String sha1hash(ObjectEntity.Type type, byte[] rawData) {
        return sha1hash(buildObjectBytes(type, rawData));
    }

    public static String sha1hash(ObjectEntity objectEntity) {
        return sha1hash(buildObjectBytes(objectEntity.getType(), objectEntity.getData()));
    }

    public static byte[] buildObjectBytes(ObjectEntity.Type type, byte[] rawData) {
        String typeStr = type.name().toLowerCase();
        String head = typeStr + " " + rawData.length + "\0";
        byte[] headBytes = head.getBytes();

        byte[] newBytes = new byte[rawData.length + headBytes.length];
        System.arraycopy(headBytes, 0, newBytes, 0, headBytes.length);
        System.arraycopy(rawData, 0, newBytes, headBytes.length, rawData.length);
        return newBytes;
    }

    public static File getObjectFile(String objectsDir, String objectId) {
        String path = ObjectUtils.path(objectId);
        Path absPath = Paths.get(objectsDir, path);
        return absPath.toFile();
    }

    public static String getObjectPath(String objectsDir, String objectId) {
        String path = ObjectUtils.path(objectId);
        Path absPath = Paths.get(objectsDir, path);
        return absPath.toString();
    }

    public static String path(String objectId) {
        String dir = objectId.substring(0, 2);
        String name = objectId.substring(2);
        return dir + File.separator + name;
    }

    public static String getModeByType(ObjectEntity.Type type) {
        switch (type) {
            case blob:
                return "100644";
            case tree:
                return "40000";
            default:
                return null;
        }
    }

    public static ObjectEntity.Type getTypeByMode(String mode) {
        switch (mode) {
            case "100644":
                return ObjectEntity.Type.blob;
            case "40000":
                return ObjectEntity.Type.tree;
            default:
                return null;
        }
    }

    public static byte[] hexToByteArray(String inHex) {
        int hexlen = inHex.length();
        byte[] result;
        if (hexlen % 2 == 1) {
            //奇数
            hexlen++;
            result = new byte[(hexlen / 2)];
            inHex = "0" + inHex;
        } else {
            //偶数
            result = new byte[(hexlen / 2)];
        }
        int j = 0;
        for (int i = 0; i < hexlen; i += 2) {
            result[j] = hexToByte(inHex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    private static byte hexToByte(String inHex) {
        return (byte) Integer.parseInt(inHex, 16);
    }


    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(aByte & 0xFF);
            if (hex.length() < 2) {
                sb.append(0);
            }
            sb.append(hex);
        }
        return sb.toString();
    }


}
