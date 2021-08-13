package com.beyond.jgit.index;

import com.beyond.jgit.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class IndexManager {

    private final ReentrantLock lock = new ReentrantLock();

    private String indexPath;

    public IndexManager(String indexPath) {
        this.indexPath = indexPath;
    }

    public void appendTo(Index.Entry entry) throws IOException {
        Index index = parseIndex(indexPath);
        save(index);
    }

    public boolean tryLock() throws IOException {
        try {
            lock.lock();
            File lockFile = new File(indexPath + ".lock");
            if (lockFile.exists()) {
                return false;
            }
            return lockFile.createNewFile();
        } finally {
            lock.unlock();
        }
    }

    public boolean unlock() throws IOException {
        try {
            lock.lock();
            File lockFile = new File(indexPath + ".lock");
            if (lockFile.exists()) {
                return lockFile.delete();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public Path toPath(File f) throws IOException {
        try {
            return f.toPath();
        } catch (InvalidPathException ex) {
            throw new IOException(ex);
        }
    }

    public void save(Index index) throws IOException {
        if (index == null){
            return;
        }
        if (CollectionUtils.isEmpty(index.getEntries())){
            return;
        }
        index.getEntries().sort(Comparator.comparing(Index.Entry::getPath));
        if (tryLock()) {
            File lockFile = new File(indexPath + ".lock");
            byte[] bytes = JsonUtils.writeValueAsBytes(index);
            if (bytes != null) {
                FileUtils.writeByteArrayToFile(lockFile, bytes);
                Files.move(toPath(lockFile), toPath(new File(indexPath)), StandardCopyOption.ATOMIC_MOVE);
            }
            return;
        }
        throw new IOException("lock failed");
    }

    public static Index parseIndex(String indexPath) throws IOException {
        File indexFile = new File(indexPath);
        if (!indexFile.exists()){
            return null;
        }
        return JsonUtils.readValue(FileUtils.readFileToByteArray(indexFile), Index.class);
    }
}
