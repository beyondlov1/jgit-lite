package com.beyond.jgit.object;


import com.beyond.jgit.pack.PackInfo;
import com.beyond.jgit.pack.PackReader;
import com.beyond.jgit.util.ObjectUtils;
import com.beyond.jgit.util.PackUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.beyond.jgit.util.ObjectUtils.EMPTY_HASH;
import static com.beyond.jgit.util.ObjectUtils.hexToByteArray;

public class ObjectManagerImpl implements ObjectManager {

    private final ObjectDb objectDb;

    public ObjectManagerImpl(String objectsDir) {
        objectDb = new ObjectDb(objectsDir);
    }

    @Override
    public String write(ObjectEntity objectEntity) throws IOException {
        byte[] bytes = objectEntity.toBytes();
        return objectDb.write(bytes);
    }

    @Override
    public ObjectEntity read(String objectId) throws IOException {
        if (objectId.equals(EMPTY_HASH)) {
            return ObjectEntity.EMPTY;
        }
        if (objectDb.existsInLoose(objectId)) {
            byte[] bytes = objectDb.read(objectId);
            return ObjectEntity.parseFrom(bytes);
        }

        String objectsDir = objectDb.getObjectsDir();
        PackInfo packInfo = PackUtils.readPackInfo(objectsDir);
        if (packInfo != null) {
            List<PackReader.PackPair> packPairs = new ArrayList<>();
            for (PackInfo.Item item : packInfo.getItems()) {
                packPairs.add(PackUtils.getPackPair(objectsDir, item.getName()));
            }
            return PackReader.readObject(objectId, packPairs);
        }

        throw new RuntimeException("object " + objectId + " not exists");
    }

    @Override
    public boolean exists(String objectId) throws IOException {
        return objectDb.exists(objectId);
    }

    @Override
    public void deleteLooseObject(String objectId) {
        objectDb.deleteLooseObject(objectId);
    }

    public static void main(String[] args) throws IOException {

        String entryPre2 = "100644 no.txt\0";
        byte[] bytes2 = hexToByteArray("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0");
        int dataLength = entryPre2.getBytes().length + bytes2.length;
        String treeHead = "tree " + dataLength + "\0";
        byte[] bytes = new byte[dataLength + treeHead.getBytes().length];

        System.arraycopy(treeHead.getBytes(), 0, bytes, 0, treeHead.getBytes().length);
        System.arraycopy(entryPre2.getBytes(), 0, bytes, treeHead.getBytes().length, entryPre2.getBytes().length);
        System.arraycopy(bytes2, 0, bytes, treeHead.getBytes().length + entryPre2.getBytes().length, bytes2.length);

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
