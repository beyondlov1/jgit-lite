package com.beyond.jgit.object;


import com.beyond.jgit.util.ObjectUtils;

import java.io.IOException;

import static com.beyond.jgit.util.ObjectUtils.EMPTY_HASH;
import static com.beyond.jgit.util.ObjectUtils.hexToByteArray;

public class ObjectManager {

    private final ObjectDb objectDb;

    public ObjectManager(String objectsDir) {
        objectDb = new ObjectDb(objectsDir);
    }

    public String write(ObjectEntity objectEntity) throws IOException {
        byte[] bytes = objectEntity.toBytes();
        return objectDb.write(bytes);
    }

    public ObjectEntity read(String objectId) throws IOException {
        if (objectId.equals(EMPTY_HASH)){
            return ObjectEntity.EMPTY;
        }
        byte[] bytes = objectDb.read(objectId);
        return ObjectEntity.parseFrom(bytes);
    }

    public boolean exists(String objectId) throws IOException {
        return objectDb.exists(objectId);
    }

    public static void main(String[] args) throws IOException {

        String entryPre2 = "100644 no.txt\0";
        byte[] bytes2 = hexToByteArray("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0");
        int dataLength = entryPre2.getBytes().length + bytes2.length;
        String treeHead = "tree " +dataLength + "\0" ;
        byte[] bytes = new byte[dataLength+treeHead.getBytes().length];

        System.arraycopy(treeHead.getBytes(), 0, bytes, 0, treeHead.getBytes().length);
        System.arraycopy(entryPre2.getBytes(), 0, bytes, treeHead.getBytes().length, entryPre2.getBytes().length);
        System.arraycopy(bytes2, 0, bytes, treeHead.getBytes().length+entryPre2.getBytes().length, bytes2.length);

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
