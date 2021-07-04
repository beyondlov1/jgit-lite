package com.beyond.jgit.object;

import com.beyond.jgit.util.ObjectUtils;
import lombok.Data;

import java.util.Arrays;

@Data
public class ObjectEntity {

    private Type type;
    /**
     * @see com.beyond.jgit.object.data.BlobObjectData
     * @see com.beyond.jgit.object.data.TreeObjectData
     * @see com.beyond.jgit.object.data.CommitObjectData
     */
    private byte[] data;

    public ObjectEntity() {
    }

    public ObjectEntity(Type type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public static ObjectEntity parseFrom(byte[] bytes){
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        int typeEnd = 0;
        int dataLengthStart = 0;
        int dataLengthEnd = 0;
        int dataStart = 0;
        int dataEnd = 0;
        int i = 0;
        for (byte aByte : bytes) {
            if (aByte == 32) {
                typeEnd = i;
                dataLengthStart = typeEnd + 1;
            }
            if (aByte == '\0') {
                dataLengthEnd = i;
                dataStart = dataLengthEnd + 1;
                break;
            }
            i++;
        }
        byte[] typeBytes = Arrays.copyOfRange(bytes, 0, typeEnd);
        byte[] dataLengthBytes = Arrays.copyOfRange(bytes, dataLengthStart, dataLengthEnd);
        dataEnd = dataStart + Integer.parseInt(new String(dataLengthBytes));
        byte[] data = Arrays.copyOfRange(bytes, dataStart, dataEnd);

        ObjectEntity objectEntity = new ObjectEntity();
        objectEntity.setType(Type.valueOf(new String(typeBytes)));
        objectEntity.setData(data);
        return objectEntity;
    }

    public byte[] toBytes(){
        return ObjectUtils.buildObjectBytes(type, data);
    }

    public enum Type {
        blob,
        tree,
        commit
        ;
    }
}
