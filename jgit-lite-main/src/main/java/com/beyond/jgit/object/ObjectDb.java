package com.beyond.jgit.object;

import com.beyond.jgit.util.ObjectUtils;
import com.beyond.jgit.util.ZlibCompression;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;


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

    public boolean exists(String objectId) {
        File file = ObjectUtils.getObjectFile(objectsDir, objectId);
        return file.exists();
    }

    public static void main(String[] args) throws IOException {
        ObjectDb objectDb = new ObjectDb("/media/beyond/70f23ead-fa6d-4628-acf7-c82133c03245/home/beyond/Documents/tmp-git");
        objectDb.write("hello".getBytes());
        byte[] read = objectDb.read(ObjectUtils.sha1hash("hello".getBytes()));
        System.out.println(new String(read));
    }
}
