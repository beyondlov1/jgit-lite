package com.beyond.jgit.object.data;

import lombok.Data;

@Data
public class BlobObjectData implements ObjectData {
    private byte[] data;

    public static BlobObjectData parseFrom(byte[] bytes) {
        BlobObjectData blobObjectData = new BlobObjectData();
        blobObjectData.setData(bytes);
        return blobObjectData;
    }

    @Override
    public byte[] toBytes() {
        return data;
    }
}
