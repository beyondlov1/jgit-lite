package com.beyond.jgit.storage;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public interface Storage {
    boolean exists(String path) throws IOException;
    void upload(String path, String targetPath) throws IOException;
    void uploadBatch(List<TransportMapping> mappings) throws IOException;
    void download(String path, String targetPath) throws IOException;
    void mkdir(Collection<String> dirPaths) throws IOException;
    void mkdir(String dir) throws IOException;
    void delete(String path) throws IOException;
    byte[] readFullyToByteArray(String path) throws IOException;
    String readFullToString(String path) throws IOException;
    String getBasePath();

    boolean move(String remotePackInfoPath, String concat, boolean overwrite) throws IOException;
    boolean copy(String remotePackInfoPath, String concat, boolean overwrite) throws IOException;
}
