package com.beyond.jgit.object;

import com.beyond.jgit.pack.PackCache;
import com.beyond.jgit.pack.PackInfo;
import com.beyond.jgit.pack.PackReader;
import com.beyond.jgit.util.ObjectUtils;
import com.beyond.jgit.util.PackUtils;
import com.beyond.jgit.util.ZlibCompression;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class ObjectDb {

    private final String objectsDir;

    public ObjectDb(String objectsDir) {
        this.objectsDir = objectsDir;
    }

    public String write(byte[] bytes) throws IOException {
        String objectId = ObjectUtils.sha1hash(bytes);
        File file = ObjectUtils.getObjectFile(objectsDir, objectId);
        FileUtils.writeByteArrayToFile(file, ZlibCompression.compressBytes(bytes));
        return objectId;
    }

    public byte[] read(String objectId) throws IOException {
        File file = ObjectUtils.getObjectFile(objectsDir, objectId);
        byte[] bytes = FileUtils.readFileToByteArray(file);
        return ZlibCompression.decompressBytes(bytes);
    }

    public boolean exists(String objectId) throws IOException {
        if (!existsInLoose(objectId)) {
            return existsInPack(objectId);
        } else {
            return true;
        }
    }

    public boolean existsInLoose(String objectId) throws IOException {
        File file = ObjectUtils.getObjectFile(objectsDir, objectId);
        return file.exists();
    }


    private boolean existsInPack(String objectId) throws IOException {
        PackInfo packInfo = PackUtils.readPackInfo(objectsDir);
        if (packInfo != null) {
            for (PackInfo.Item item : packInfo.getItems()) {
                PackReader.PackPair packPair = PackUtils.getPackPair(objectsDir, item.getName());
                if (existsInPack(objectId, packPair)){
                    return true;
                }
            }
        }
        return false;
    }

    public boolean existsInPack(String objectId, PackReader.PackPair packPair) {
        Set<String> allObjectIds;
        if (CollectionUtils.isNotEmpty(PackCache.get(packPair))){
            allObjectIds = new HashSet<>(PackCache.get(packPair));
        }else {
            allObjectIds = new HashSet<>(PackReader.readAllObjectIds(packPair));
        }
        return allObjectIds.contains(objectId);
    }

    public boolean existsInPack(String objectId, Collection<PackReader.PackPair> packPairs) {
        Set<String> allObjectIds = new HashSet<>(PackReader.readAllObjectIds(packPairs));
        return allObjectIds.contains(objectId);
    }

    public void deleteLooseObject(String objectId) {
        File file = ObjectUtils.getObjectFile(objectsDir, objectId);
        FileUtils.deleteQuietly(file);
    }

    public String getObjectsDir() {
        return objectsDir;
    }

    public static void main(String[] args) throws IOException {
        ObjectDb objectDb = new ObjectDb("/media/beyond/70f23ead-fa6d-4628-acf7-c82133c03245/home/beyond/Documents/tmp-git");
        objectDb.write("hello".getBytes());
        byte[] read = objectDb.read(ObjectUtils.sha1hash("hello".getBytes()));
        System.out.println(new String(read));
    }
}
