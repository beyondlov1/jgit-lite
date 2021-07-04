package com.beyond.jgit.object.data;

import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.util.BytesUtils;
import com.beyond.jgit.util.ObjectUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.beyond.jgit.util.ObjectUtils.hexToByteArray;

@Data
public class TreeObjectData implements ObjectData {
    private List<TreeEntry> entries = new ArrayList<>();

    public static TreeObjectData parseFrom(byte[] bytes) {
        TreeObjectData treeObjectData = new TreeObjectData();

        int offset = 0;
        int entryEndIndex;
        while(true)  {
            int index = BytesUtils.indexUntil(bytes, offset, (byte) '\0');
            if (index < 0){
                break;
            }
            entryEndIndex = index + 21;
            byte[] entry = new byte[entryEndIndex - offset];
            System.arraycopy(bytes, offset, entry, 0, entry.length);
            TreeEntry treeEntry = TreeEntry.parseFrom(entry);
            treeObjectData.getEntries().add(treeEntry);
            offset = entryEndIndex;
        }
        return treeObjectData;
    }

    @Override
    public byte[] toBytes() {
        TreeObjectData treeObjectData = this;
        List<byte[]> entryBytes = new ArrayList<>();
        for (TreeEntry entry : treeObjectData.getEntries()) {
            String entryPre = entry.getMode() + " " + entry.getName() + "\0";
            byte[] objectIdBytes = hexToByteArray(entry.getObjectId());
            entryBytes.add(entryPre.getBytes());
            entryBytes.add(objectIdBytes);
        }
        int dataLength = 0;
        for (byte[] entryByte : entryBytes) {
            dataLength += entryByte.length;
        }
        byte[] bytes = new byte[dataLength];
        int flag = 0;
        for (byte[] bs : entryBytes) {
            System.arraycopy(bs, 0, bytes, flag, bs.length);
            flag += bs.length;
        }
        return bytes;
    }

    @Data
    public static class TreeEntry {
        private String mode;
        private ObjectEntity.Type type;
        private String name;
        private String objectId;

        public static TreeEntry parseFrom(byte[] entryBytes) {
            byte[] modeBytes = BytesUtils.collectUntil(entryBytes, 0, (byte) ' ');
            String mode = new String(modeBytes);
            ObjectEntity.Type type = ObjectUtils.getTypeByMode(mode);

            byte[] nameBytes = BytesUtils.collectUntil(entryBytes, modeBytes.length + 1, (byte) '\0');
            String name = new String(nameBytes);

            byte[] objectIdBytes = BytesUtils.collectAfter(entryBytes, modeBytes.length + nameBytes.length + 2);
            String objectId = ObjectUtils.bytesToHex(objectIdBytes);

            TreeEntry treeEntry = new TreeEntry();
            treeEntry.setMode(mode);
            treeEntry.setType(type);
            treeEntry.setName(name);
            treeEntry.setObjectId(objectId);
            return treeEntry;
        }
    }

}
