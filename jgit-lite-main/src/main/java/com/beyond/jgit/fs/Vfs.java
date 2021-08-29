package com.beyond.jgit.fs;

import java.io.IOException;

public interface Vfs {
    default boolean copy(String path, String targetPath) throws IOException{
        return copy(path, targetPath, false);
    }

    boolean copy(String path, String targetPath, boolean overWrite) throws IOException;

    default boolean move(String path, String targetPath) throws IOException{
        return move(path, targetPath, false);
    }

    boolean move(String path, String targetPath, boolean overWrite) throws IOException;

    boolean delete(String path);

    boolean exists(String path);

    void write(String path, byte[] data) throws IOException;

    byte[] readBytes(String path) throws IOException;

    String readString(String path) throws IOException;

    void mkdir(String dir) throws IOException;

}
