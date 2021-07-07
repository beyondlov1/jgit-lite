package com.beyond.jgit.object;

import com.beyond.jgit.util.ObjectUtils;
import lombok.Data;

import java.util.Arrays;

@Data
public class ObjectEntity {

    public static final ObjectEntity EMPTY = new ObjectEntity(Type.commit, new byte[0]);

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

    public static ObjectEntity parseFrom(byte[] bytes) {
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

    public byte[] toBytes() {
        return ObjectUtils.buildObjectBytes(type, data);
    }

    public boolean isEmpty(){
        return data == null || data.length == 0;
    }

    public enum Type {
        /**
         * commit
         */
        commit(1),


        /**
         * tree
         */
        tree(2),

        /**
         * blob
         */
        blob(3)
        ;

        /**
         * value
         */
        private int val;

        Type(int val) {
            this.val = val;
        }

        public static Type of(int type) {
            switch (type) {
                case 1:
                    return commit;
                case 2:
                    return tree;
                case 3:
                    return blob;
                default:
                    throw new RuntimeException("type error");
            }
        }

        public int getVal() {
            return val;
        }
        }
}
