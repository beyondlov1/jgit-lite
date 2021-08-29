package com.beyond.jgit.fs;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileVfs implements Vfs{
    @Override
    public boolean copy(String path, String targetPath, boolean overWrite) throws IOException {
        if (overWrite){
            Files.copy( Paths.get(path), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        }else{
            if (exists(targetPath)){
                return false;
            }else{
                Files.copy( Paths.get(path), Paths.get(targetPath), StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
        }
    }

    @Override
    public boolean move(String path, String targetPath, boolean overWrite) throws IOException {
        if (overWrite){
            Files.move( Paths.get(path), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        }else{
            if (exists(targetPath)){
                return false;
            }else{
                Files.move( Paths.get(path), Paths.get(targetPath), StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
        }
    }

    @Override
    public boolean delete(String path) {
        if (exists(path)){
            FileUtils.deleteQuietly(new File(path));
            return true;
        }else {
            return false;
        }
    }

    @Override
    public boolean exists(String path) {
        return new File(path).exists();
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        FileUtils.writeByteArrayToFile(new File(path),data);
    }

    @Override
    public byte[] readBytes(String path) throws IOException {
        if (exists(path)){
            return FileUtils.readFileToByteArray(new File(path));
        }else {
            return null;
        }
    }

    @Override
    public String readString(String path) throws IOException {
        byte[] data = readBytes(path);
        if (data == null){
            return null;
        }
        return new String(data);
    }

    @Override
    public void mkdir(String dir) throws IOException {
        FileUtils.forceMkdir(new File(dir));
    }
}
