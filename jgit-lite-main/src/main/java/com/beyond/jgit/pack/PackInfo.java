package com.beyond.jgit.pack;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PackInfo {

    private List<Item> items = new ArrayList<>();

    public void add(String name){
        Item item = new Item();
        item.setName(name);
        items.add(item);
    }

    @Data
    public static class Item {
        private String name;
    }
}
