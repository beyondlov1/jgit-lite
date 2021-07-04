package com.beyond.jgit.storage;

import com.beyond.jgit.util.PathUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
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
    public void upload(File file, String targetPath) throws IOException {
        FileUtils.copyFile(file, new File(PathUtils.concat(basePath, targetPath)));
    }

    @Override
    public void uploadBatch(List<TransportMapping> mappings) throws IOException {
        for (TransportMapping mapping : mappings) {
            upload(new File(mapping.getLocalPath()), mapping.getRemotePath());
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
    public void download(String path, File targetFile) throws IOException {
        FileUtils.copyFile(new File(PathUtils.concat(basePath, path)),targetFile);
    }

    @Override
    public byte[] readFullyToByteArray(String path) throws IOException {
        return FileUtils.readFileToByteArray(new File(PathUtils.concat(basePath,path)));
    }

    @Override
    public String getBasePath() {
        return basePath;
    }
}
