package com.beyond.jgit.storage;

import com.beyond.jgit.util.PathUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;

public class FileStorage extends AbstractStorage {

    private final String basePath;

    public FileStorage(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public boolean exists(String path) {
        return new File(PathUtils.concat(basePath, path)).exists();
    }

    @Override
    public void upload(String path, String targetPath) throws IOException {
        FileUtils.copyFile(new File(path), new File(PathUtils.concat(basePath, targetPath)));
    }

    @Override
    public void uploadBatch(List<TransportMapping> mappings) throws IOException {
        for (TransportMapping mapping : mappings) {
            upload(mapping.getLocalPath(), mapping.getRemotePath());
        }
    }

    @Override
    public void mkdir(Collection<String> dirPaths) throws IOException {
        for (String dirPath : dirPaths) {
            FileUtils.forceMkdir(new File(PathUtils.concat(basePath,dirPath)));
        }
    }

    @Override
    public void mkdir(String dir) throws IOException {
        FileUtils.forceMkdir(new File(PathUtils.concat(basePath,dir)));
    }

    @Override
    public void delete(String path) throws IOException {
        FileUtils.forceDelete(new File(PathUtils.concat(basePath,path)));
    }


    @Override
    public void download(String path, String targetPath) throws IOException {
        FileUtils.copyFile(new File(PathUtils.concat(basePath, path)), new File(targetPath));
    }

    @Override
    public byte[] readFullyToByteArray(String path) throws IOException {
        return FileUtils.readFileToByteArray(new File(PathUtils.concat(basePath,path)));
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    @Override
    public boolean move(String sourcePath, String targetPath, boolean overwrite) throws IOException {
        File sourceFile = new File(getAbsPath(sourcePath));
        if (!sourceFile.exists()){
            return false;
        }
        if (overwrite){
            Files.move(sourceFile.toPath(), new File(getAbsPath(targetPath)).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }else{
            Files.move(sourceFile.toPath(), new File(getAbsPath(targetPath)).toPath(), StandardCopyOption.ATOMIC_MOVE);
        }
        return true;
    }

    private String getAbsPath(String relativePath){
        return PathUtils.concat(getBasePath(), relativePath);
    }

    @Override
    public boolean copy(String sourcePath, String targetPath, boolean overwrite) throws IOException {
        File sourceFile = new File(getAbsPath(sourcePath));
        if (!sourceFile.exists()){
            return false;
        }
        if (overwrite){
            Files.copy(sourceFile.toPath(), new File(getAbsPath(targetPath)).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }else{
            Files.copy(sourceFile.toPath(), new File(getAbsPath(targetPath)).toPath(), StandardCopyOption.ATOMIC_MOVE);
        }
        return true;
    }
}
