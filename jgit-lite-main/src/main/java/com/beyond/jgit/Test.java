package com.beyond.jgit;

import com.beyond.delta.DeltaUtils;
import com.beyond.jgit.util.ObjectUtils;

import java.io.IOException;

import static com.beyond.jgit.util.ObjectUtils.hexToByteArray;

public class Test {

    public static void main(String[] args) throws IOException {

        String entryPre1 = "40000 .idea\0";
        byte[] bytes1 = hexToByteArray("fee37ac3388bc1004ff4cf4908872889964f7000");
        String entryPre2 = "100644 hello6.txt\0";
        byte[] bytes2 = hexToByteArray("c0bdf8ef4f86c2cff039aa46832e321574ee1a27");
        int dataLength = entryPre1.getBytes().length + bytes1.length + entryPre2.getBytes().length + bytes2.length;
        String treeHead = "tree " +dataLength + "\0" ;
        byte[] bytes = new byte[dataLength+treeHead.length()];

        System.arraycopy(treeHead.getBytes(), 0, bytes, 0, treeHead.getBytes().length);
        System.arraycopy(entryPre1.getBytes(), 0, bytes, treeHead.getBytes().length, entryPre1.getBytes().length);
        System.arraycopy(bytes1, 0, bytes, treeHead.getBytes().length+entryPre1.getBytes().length, bytes1.length);
        System.arraycopy(entryPre2.getBytes(), 0, bytes, treeHead.getBytes().length+entryPre1.getBytes().length+bytes1.length, entryPre2.getBytes().length);
        System.arraycopy(bytes2, 0, bytes, treeHead.getBytes().length+entryPre1.getBytes().length+bytes1.length+entryPre2.getBytes().length, bytes2.length);

        String s = ObjectUtils.sha1hash(bytes);
        System.out.println(s);

//        ObjectManager objectManager = new ObjectManager("/media/beyond/70f23ead-fa6d-4628-acf7-c82133c03245/home/beyond/Documents/tmp-git");
//        ObjectEntity objectEntity = new ObjectEntity(ObjectEntity.Type.blob, "helloworld".getBytes());
//        String objectId = objectManager.write(objectEntity);
//        System.out.println(objectId);
//
//        ObjectEntity objectEntity1 = objectManager.read(objectId);
//        System.out.println(new String(objectEntity1.getData()));
//        System.out.println(objectEntity1.getType());

    }
}
