package com.beyond.jgit.pack;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class PackIndex {

    private byte[] packFileChecksum;
    private List<Item> items = new ArrayList<>();

    public static PackIndex newInstance(){
        return new PackIndex();
    }

    public void add(String objectId, int offset){
        Set<String> existedObjectIds = items.stream().map(Item::getObjectId).collect(Collectors.toSet());
        if (existedObjectIds.contains(objectId)) {
            return;
        }
        items.add(new Item(objectId, offset));
    }

    @Data
    public static class Item{
        private String objectId;
        private int offset;

        public Item(String objectId, int offset) {
            this.objectId = objectId;
            this.offset = offset;
        }
    }
}
