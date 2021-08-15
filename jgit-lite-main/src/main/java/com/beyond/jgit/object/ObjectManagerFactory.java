package com.beyond.jgit.object;

import java.util.HashMap;
import java.util.Map;

public class ObjectManagerFactory {

    private final static Map<String, ObjectManager> OBJECT_MANAGER_MAP = new HashMap<>();

    public static ObjectManager get(String objectDir){
        ObjectManager objectManager = OBJECT_MANAGER_MAP.get(objectDir);
        if (objectManager == null){
            objectManager = new ObjectManagerCacheable(objectDir);
            OBJECT_MANAGER_MAP.put(objectDir, objectManager);
            return objectManager;
        }
        return objectManager;
    }
}
