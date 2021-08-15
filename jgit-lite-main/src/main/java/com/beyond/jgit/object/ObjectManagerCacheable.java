package com.beyond.jgit.object;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ObjectManagerCacheable implements ObjectManager {

    private static final int CACHE_SIZE = 5000;

    private final Map<String, ObjectEntity> objectId2ObjectEntityCache = new HashMap<>();

    private final ObjectManager objectManager;

    public ObjectManagerCacheable(String objectDir) {
        this.objectManager = new ObjectManagerImpl(objectDir);
    }

    public ObjectManagerCacheable(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Override
    public String write(ObjectEntity objectEntity) throws IOException {
        return objectManager.write(objectEntity);
    }

    @Override
    public ObjectEntity read(String objectId) throws IOException {
        if (objectId2ObjectEntityCache.containsKey(objectId)){
            return objectId2ObjectEntityCache.get(objectId);
        }
        ObjectEntity result = objectManager.read(objectId);
        putCache(objectId, result);
        return result;
    }

    @Override
    public boolean exists(String objectId) throws IOException {
        if (objectId2ObjectEntityCache.get(objectId) != null && objectId2ObjectEntityCache.get(objectId) != ObjectEntity.EMPTY ){
            return true;
        }
        return objectManager.exists(objectId);
    }

    @Override
    public void deleteLooseObject(String objectId) {
        objectManager.deleteLooseObject(objectId);
    }

    private void putCache(String commitObjectId, ObjectEntity objectEntity){
        if (objectId2ObjectEntityCache.size() > CACHE_SIZE){
            objectId2ObjectEntityCache.entrySet().iterator().remove();
        }
        objectId2ObjectEntityCache.put(commitObjectId, objectEntity);
    }
}
