package com.beyond.jgit.pack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PackCache {
    private static ConcurrentHashMap<PackReader.PackPair, Set<String>> packedObjectIds = new ConcurrentHashMap<>();

    public static void add(PackReader.PackPair key, String val){
        packedObjectIds.putIfAbsent(key, new HashSet<>());
        packedObjectIds.get(key).add(val);
    }

    public static void addAll(PackReader.PackPair key, List<String> val){
        packedObjectIds.putIfAbsent(key, new HashSet<>());
        packedObjectIds.get(key).addAll(val);
    }

    public static void clear(){
        packedObjectIds.clear();
    }

    public static Set<String> get(PackReader.PackPair packPair) {
        return packedObjectIds.get(packPair);
    }
}
