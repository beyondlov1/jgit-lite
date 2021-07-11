package com.beyond.jgit.pack;

import com.beyond.delta.DeltaUtils;
import com.beyond.jgit.object.ObjectEntity;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PackReader {
    public static ObjectEntity readObject(String objectId, File packDataFile, File packIndexFile) throws IOException {
        byte[] packFileBytes = FileUtils.readFileToByteArray(packDataFile);
        byte[] packIndexBytes = FileUtils.readFileToByteArray(packIndexFile);
        return readObject(objectId, packFileBytes, packIndexBytes);
    }

    public static ObjectEntity readObject(String objectId, byte[] packFileBytes, byte[] packIndexBytes) throws IOException {
        int offsetInPackFile = PackIndexFormatter.indexForOffset(packIndexBytes, objectId);
        Block block = PackFileFormatter.parseNextBlock(packFileBytes, offsetInPackFile);
        ObjectEntity result = new ObjectEntity();
        if (block instanceof BaseBlock) {
            result.setType(((BaseBlock) block).getType());
            result.setData(((BaseBlock) block).getContent());
            return result;
        }
        if (block instanceof RefDeltaBlock) {
            String ref = ((RefDeltaBlock) block).getRef();
            ObjectEntity refObjectEntity = readObject(ref, packFileBytes, packIndexBytes);
            byte[] data = DeltaUtils.applyDeltas(((RefDeltaBlock) block).getDeltas(), refObjectEntity.getData());
            result.setType(refObjectEntity.getType());
            result.setData(data);
            return result;
        }

        if (block instanceof OfsDeltaBlock) {
            // not used
            throw new RuntimeException("OfsDeltaBlock is not supported yet");
        }

        throw new RuntimeException("read failed");
    }

    public static List<ObjectEntity>  readObjects(Collection<String> objectIds, byte[] packFileBytes, byte[] packIndexBytes) throws IOException {
        List<ObjectEntity> result = new ArrayList<>();
        for (String objectId : objectIds) {
            result.add(readObject(objectId, packFileBytes, packIndexBytes));
        }
        return result;
    }



    public static List<ObjectEntity> readObjects(Collection<String> objectIds, File packDataFile, File packIndexFile) throws IOException {
        byte[] packFileBytes = FileUtils.readFileToByteArray(packDataFile);
        byte[] packIndexBytes = FileUtils.readFileToByteArray(packIndexFile);
        return readObjects(objectIds, packFileBytes, packIndexBytes);
    }

    public static List<ObjectEntity> readAllObjects(byte[] packFileBytes, byte[] packIndexBytes) throws IOException {
        List<PackIndex.Item> items = PackIndexFormatter.parse(packIndexBytes);
        List<String> objectIds = items.stream().map(PackIndex.Item::getObjectId).collect(Collectors.toList());
        return readObjects(objectIds, packFileBytes, packIndexBytes);
    }

    public static List<ObjectEntity> readAllObjects( File packDataFile, File packIndexFile) throws IOException {
        byte[] packFileBytes = FileUtils.readFileToByteArray(packDataFile);
        byte[] packIndexBytes = FileUtils.readFileToByteArray(packIndexFile);
        return readAllObjects(packFileBytes, packIndexBytes);
    }

}
