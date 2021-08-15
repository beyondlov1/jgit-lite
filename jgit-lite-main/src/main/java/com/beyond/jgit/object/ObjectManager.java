package com.beyond.jgit.object;


import java.io.IOException;

public interface ObjectManager {

    String write(ObjectEntity objectEntity) throws IOException;

    ObjectEntity read(String objectId) throws IOException;

    boolean exists(String objectId) throws IOException;

    void deleteLooseObject(String objectId);

}
