package com.beyond.jgit.pack;

import com.beyond.delta.DeltaUtils;
import com.beyond.jgit.object.ObjectEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class PackReader {

    public static ObjectEntity readObject(String objectId, List<PackPair> packPairs) throws IOException {
        log.debug("reading from pack, objectId:"+objectId);
        for (PackPair packPair : packPairs) {
            byte[] packIndexBytes = packPair.getPackIndexBytes();
            int offsetInPackFile = PackIndexFormatter.indexForOffset(packIndexBytes, objectId);
            if (offsetInPackFile < 0) {
                continue;
            }
            byte[] packFileBytes = packPair.getPackDataBytes();
            Block block = PackFileFormatter.parseNextBlock(packFileBytes, offsetInPackFile);
            ObjectEntity result = new ObjectEntity();
            if (block instanceof BaseBlock) {
                result.setType(((BaseBlock) block).getType());
                result.setData(((BaseBlock) block).getContent());
                return result;
            }
            if (block instanceof RefDeltaBlock) {
                String ref = ((RefDeltaBlock) block).getRef();
                ObjectEntity refObjectEntity = readObject(ref, packPairs);
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
        throw new RuntimeException("read　failed");
    }

    public static List<ObjectEntity> readObjects(Collection<String> objectIds, List<PackPair> packPairs) throws IOException {
        List<ObjectEntity> result = new ArrayList<>();
        for (String objectId : objectIds) {
            result.add(readObject(objectId, packPairs));
        }
        return result;
    }

    public static List<ObjectEntity> readAllObjects(List<PackPair> packPairs) throws IOException {
        return readObjects(readAllObjectIds(packPairs), packPairs);
    }

    public static List<String> readAllObjectIds(Collection<PackPair> packPairs){
        List<String> allObjectIds = new ArrayList<>();
        packPairs.forEach(PackReader::readAllObjectIds);
        return allObjectIds;
    }

    public static List<String> readAllObjectIds(PackPair packPair){
        List<String> allObjectIds = new ArrayList<>();
        try {
            List<PackIndex.Item> parsedItems = PackIndexFormatter.parse(packPair.getPackIndexBytes());
            List<String> objectIds = parsedItems.stream().map(PackIndex.Item::getObjectId).collect(Collectors.toList());
            allObjectIds.addAll(objectIds);
            PackCache.addAll(packPair, objectIds);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return allObjectIds;
    }

    public static class PackPair {
        private final File packIndexFile;
        private final File packDataFile;
        private byte[] packIndexBytes;
        private byte[] packDataBytes;

        public PackPair(File packIndexFile, File packDataFile) {
            this.packIndexFile = packIndexFile;
            this.packDataFile = packDataFile;
        }

        public byte[] getPackIndexBytes() throws IOException {
            if (packIndexBytes == null) {
                packIndexBytes = FileUtils.readFileToByteArray(packIndexFile);
            }
            return packIndexBytes;
        }

        public byte[] getPackDataBytes() throws IOException {
            if (packDataBytes == null) {
                packDataBytes = FileUtils.readFileToByteArray(packDataFile);
            }
            return packDataBytes;
        }

        public File getPackIndexFile() {
            return packIndexFile;
        }

        public File getPackDataFile() {
            return packDataFile;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PackPair packPair = (PackPair) o;
            return Objects.equals(packIndexFile, packPair.packIndexFile) &&
                    Objects.equals(packDataFile, packPair.packDataFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packIndexFile, packDataFile);
        }
    }
}
