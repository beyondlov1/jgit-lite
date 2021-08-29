package com.beyond.jgit.storage;

import com.beyond.jgit.util.JsonUtils;
import com.beyond.jgit.util.ObjectUtils;
import com.beyond.jgit.util.PathUtils;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;
import com.thegrizzlylabs.sardineandroid.impl.SardineException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SardineStorage extends AbstractStorage {

    private final String basePath;

    private Sardine sardine;

    private final ExistDirCacheManager existDirs;

    private final String sessionDir;

    public SardineStorage(String basePath, String username, String password) {
        this(basePath, username, password, null, null);
    }

    public SardineStorage(String basePath, String username, String password, String existDirCachePath, String sessionDir) {
        this.basePath = basePath;
        sardine = new LoggedSardine(new OkHttpSardine());
        sardine.setCredentials(username, password);
        existDirs = new ExistDirCacheManager(existDirCachePath);
        this.sessionDir = sessionDir;
    }

    @Override
    public boolean exists(String path) throws IOException {
        String absPath = getAbsPath(path);
        if (existDirs.contains(absPath)) {
            return true;
        }
        return sardine.exists(absPath);
    }

    @Override
    public void upload(String path, String targetPath) throws IOException {
        String absPath = getAbsPath(targetPath);
        forceMkdirParent(absPath);
        sardine.put(absPath, new File(path), null);
    }


    @Override
    public void uploadBatch(List<TransportMapping> mappings) throws IOException {
        Session session = new Session(sessionDir, Session.generateSessionId(Session.TransportType.upload,mappings));
        session.start(mappings);
        for (TransportMapping mapping : mappings) {
            if (session.isDone(mapping)){
                log.debug("uploaded, no upload again. if something is wrong, delete session:{}", PathUtils.concat(sessionDir,session.getSessionId()));
                continue;
            }
            upload(mapping.getLocalPath(), mapping.getRemotePath());
            session.done(mapping);
        }
        session.complete();
    }

    private void forceMkdirParent(String absPath) throws IOException {
        String parent;
        if (existDirs.contains(PathUtils.parent(absPath))) {
            return;
        }
        if (!sardine.exists(parent = PathUtils.parent(absPath))) {
            forceMkdirParent(parent);
            sardine.createDirectory(parent);
            existDirs.add(parent);
        }
        existDirs.add(parent);
    }

    @Override
    public void download(String path, String targetPath) throws IOException {
        String absPath = getAbsPath(path);
        InputStream inputStream = sardine.get(absPath);
        FileUtils.copyInputStreamToFile(inputStream, new File(targetPath));
    }


    @Override
    public void mkdir(Collection<String> dirPaths) throws IOException {
        for (String dirPath : dirPaths) {
            mkdir(dirPath);
        }
    }

    @Override
    public void mkdir(String dir) throws IOException {
        String absDir = getAbsPath(dir);
        if (existDirs.contains(absDir)) {
            return;
        }
        if (!sardine.exists(absDir)) {
            sardine.createDirectory(absDir);
            existDirs.add(absDir);
        }
        existDirs.add(absDir);
    }

    @Override
    public void delete(String path) throws IOException {
        try {
            String absPath = getAbsPath(path);
            sardine.delete(absPath);
            existDirs.delete(absPath);
        } catch (SardineException e) {
            if (e.getStatusCode() != 404) {
                throw e;
            }
        }
    }

    @Override
    public byte[] readFullyToByteArray(String path) throws IOException {
        String absPath = getAbsPath(path);
        InputStream inputStream = sardine.get(absPath);
        return IOUtils.toByteArray(inputStream);
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    private String getAbsPath(String path) {
        return PathUtils.concat(getBasePath(), path.replace(File.separator, "/"));
    }

    @Override
    public boolean move(String source, String target, boolean overwrite) throws IOException {
        if (!exists(source)){
            return false;
        }
        sardine.move(getAbsPath(source), getAbsPath(target), overwrite);
        return true;
    }

    @Override
    public boolean copy(String source, String target, boolean overwrite) throws IOException {
        if (!exists(source)){
            return false;
        }
        sardine.copy(getAbsPath(source), getAbsPath(target), overwrite);
        return true;
    }

    private static class ExistDirCacheManager {

        private String cachePath;

        private final Set<String> existDirs = new HashSet<>();

        public ExistDirCacheManager(String cachePath) {
            if (StringUtils.isNotBlank(cachePath)) {
                this.cachePath = cachePath;
                String s = null;
                try {
                    s = FileUtils.readFileToString(new File(cachePath), StandardCharsets.UTF_8);
                } catch (IOException ignore) {
                }
                if (StringUtils.isNotBlank(s)) {
                    String[] split = StringUtils.split(s, "\n");
                    existDirs.addAll(Arrays.asList(split));
                }
            }
        }

        public void add(String relativePath) throws IOException {
            if (existDirs.contains(relativePath)) {
                return;
            }
            if (StringUtils.isNotBlank(cachePath)) {
                FileUtils.writeLines(new File(cachePath), Collections.singletonList(relativePath), true);
            }
            existDirs.add(relativePath);
        }

        public boolean contains(String path) {
            return existDirs.contains(path);
        }

        public void delete(String path) throws IOException {
            if (contains(path)) {
                existDirs.remove(path);
                FileUtils.writeLines(new File(cachePath), existDirs);
            }
        }
    }

    @Data
    private static class Session {

        private String sessionDir;
        private String sessionId;

        public Session(String sessionDir, String sessionId) {
            this.sessionDir = sessionDir;
            this.sessionId = sessionId;
        }

        public static String generateSessionId(TransportType transportType, List<TransportMapping> transportMappings) {
            List<TransportMapping> sorted = transportMappings.stream().sorted(Comparator.comparing(x -> x.getLocalPath() + " " + x.getRemotePath())).collect(Collectors.toList());
            return ObjectUtils.sha1hash((transportType.toString()+" "+JsonUtils.writeValueAsString(sorted)).getBytes());
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        public void start(List<TransportMapping> transportMappings) throws IOException {
            File thisSessionDir = new File(PathUtils.concat(sessionDir, sessionId));
            if (!thisSessionDir.exists()){
                thisSessionDir.mkdirs();
            }
            for (TransportMapping transportMapping : transportMappings) {
                File file = new File(PathUtils.concat(sessionDir, sessionId, transportMapping.toSha1hash()));
                if (!file.exists()){
                    file.createNewFile();
                }
            }
        }

        public void done(TransportMapping mapping) throws IOException {
            File file = new File(PathUtils.concat(sessionDir, sessionId, mapping.toSha1hash()));
            if (file.exists()){
                FileUtils.forceDelete(file);
            }
        }

        public boolean isDone(TransportMapping mapping) {
            // 根据mapping的sha1hash对应路径的文件是否存在来判断是否已经传输完成
            File thisSessionDir = new File(PathUtils.concat(sessionDir, sessionId));
            if (thisSessionDir.exists()){
                return !new File(PathUtils.concat(sessionDir, sessionId, mapping.toSha1hash())).exists();
            }
            return false;
        }

        public void complete() throws IOException {
            File thisSessionDir = new File(PathUtils.concat(sessionDir, sessionId));
            if (thisSessionDir.exists()){
                FileUtils.forceDelete(thisSessionDir);
            }
        }

        private enum TransportType{
            upload,download
        }
    }
}
