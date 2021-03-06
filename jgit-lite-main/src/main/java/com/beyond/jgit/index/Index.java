package com.beyond.jgit.index;

import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.object.ObjectManager;
import com.beyond.jgit.object.data.CommitObjectData;
import com.beyond.jgit.object.data.TreeObjectData;
import com.beyond.jgit.util.FileUtil;
import com.beyond.jgit.util.JsonUtils;
import com.beyond.jgit.util.ObjectUtils;
import com.beyond.jgit.util.PathUtils;
import lombok.Data;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.beyond.jgit.GitLite.EMPTY_OBJECT_ID;

@Data
public class Index {

    private static Map<String, List<Entry>> commitObjectId2BlobAndTreeEntriesCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private static Map<String, List<Entry>> commitObjectId2BlobEntriesCache = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final int CACHE_SIZE = 1000;

    private List<Entry> entries = new ArrayList<>();

    public void upsert(Collection<Entry> entries) {
        Map<String, Entry> tmpMap = this.entries.stream().collect(Collectors.toMap(Entry::getPath, x -> x, (v1, v2) -> v2));
        tmpMap.putAll(entries.stream().collect(Collectors.toMap(Entry::getPath, x -> x, (v1, v2) -> v2)));
        this.entries.clear();
        this.entries.addAll(tmpMap.values());
        this.entries.sort(Comparator.comparing(Entry::getPath));
    }

    public void remove(Collection<Entry> entries) {
        Set<String> pathsToRemove = entries.stream().map(Entry::getPath).collect(Collectors.toSet());
        this.entries.removeIf(entry -> pathsToRemove.contains(entry.getPath()));
    }

    @Data
    public static class Entry {
        private String path;
        private String objectId;
        private ObjectEntity.Type type;
        private Flag flag = Flag.NONE;

        public enum Flag {
            NONE
        }
    }

    public static Index generateFromLocalDir(String localDir) throws IOException {
        Collection<File> files = FileUtil.listChildOnlyFilesWithoutDirOf(localDir, ".git");
        Index index = new Index();
        List<Entry> entries = index.getEntries();
        for (File file : files) {
            Entry entry = new Entry();
            entry.setPath(PathUtils.getRelativePath(localDir, file.getAbsolutePath()));
            entry.setObjectId(ObjectUtils.sha1hash(ObjectEntity.Type.blob, file));
            entries.add(entry);
        }
        entries.sort(Comparator.comparing(Entry::getPath));
        return index;
    }


    public static Index generateFromIndexFile(File indexFile) throws IOException {
        byte[] bytes = FileUtils.readFileToByteArray(indexFile);
        if (bytes != null) {
            return JsonUtils.readValue(bytes, Index.class);
        }
        return null;
    }


    public static Index generateFromCommit(String commitObjectId, ObjectManager objectManager) throws IOException {
        if (commitObjectId == null) {
            return null;
        }
        if (Objects.equals(commitObjectId, EMPTY_OBJECT_ID)) {
            return null;
        }
        ObjectEntity commit = objectManager.read(commitObjectId);
        return generateFromCommit(commit, objectManager);
    }

    public static Index generateFromCommit(ObjectEntity commit, ObjectManager objectManager) throws IOException {
        String commitObjectId = ObjectUtils.sha1hash(commit.getType(), commit.getData());
        List<Entry> entries;

        List<Entry> fromCache = getBlobFromCache(commitObjectId);
        if (fromCache != null){
            entries = fromCache;
        } else {
            entries = new ArrayList<>();
            CommitObjectData commitObjectData = CommitObjectData.parseFrom(commit.getData());
            walk(commitObjectData.getTree(), "", objectManager, entries);
            entries.sort(Comparator.comparing(Entry::getPath));
            putBlobCache(commitObjectId, entries);
        }

        Index index = new Index();
        index.setEntries(entries);
        return index;
    }

    @SuppressWarnings("DuplicatedCode")
    private static void walk(String treeObjectId, String parentPath, ObjectManager objectManager, List<Entry> entries) throws IOException {
        ObjectEntity tree = objectManager.read(treeObjectId);
        TreeObjectData treeData = TreeObjectData.parseFrom(tree.getData());
        for (TreeObjectData.TreeEntry treeEntry : treeData.getEntries()) {
            if (treeEntry.getType() == ObjectEntity.Type.blob) {
                Entry entry = new Entry();
                entry.setObjectId(treeEntry.getObjectId());
                entry.setPath(PathUtils.concat(parentPath, treeEntry.getName()));
                entry.setType(ObjectEntity.Type.blob);
                entries.add(entry);
            }
            if (treeEntry.getType() == ObjectEntity.Type.tree) {
                walk(treeEntry.getObjectId(), PathUtils.concat(parentPath, treeEntry.getName()), objectManager, entries);
            }
        }
    }


    public static List<Entry> generateTreeAndBlobFromCommit(String commitObjectId, ObjectManager objectManager) throws IOException {
        if (commitObjectId == null) {
            return null;
        }
        if (Objects.equals(commitObjectId, EMPTY_OBJECT_ID)) {
            return null;
        }

        List<Entry> fromCache = getBlobAndTreeFromCache(commitObjectId);
        if (fromCache != null){
            return fromCache;
        }

        ObjectEntity commit = objectManager.read(commitObjectId);

        List<Entry> entries = new ArrayList<>();
        CommitObjectData commitObjectData = CommitObjectData.parseFrom(commit.getData());

        Entry rootTreeEntry = new Entry();
        rootTreeEntry.setObjectId(commitObjectData.getTree());
        rootTreeEntry.setPath("");
        rootTreeEntry.setType(ObjectEntity.Type.tree);

        walkTreeAndBlob(rootTreeEntry, objectManager, entries);
        entries.sort(Comparator.comparing(Entry::getPath));
        putBlobAndTreeCache(commitObjectId, entries);
        return entries;
    }

    @SuppressWarnings("DuplicatedCode")
    public static void walkTreeAndBlob(Entry treeEntity, ObjectManager objectManager, List<Entry> entries) throws IOException {

        if (treeEntity.getType() != ObjectEntity.Type.tree) {
            throw new RuntimeException("????????????");
        }
        entries.add(treeEntity);

        ObjectEntity tree = objectManager.read(treeEntity.getObjectId());
        TreeObjectData treeData = TreeObjectData.parseFrom(tree.getData());
        for (TreeObjectData.TreeEntry treeEntry : treeData.getEntries()) {
            Entry entry = new Entry();
            entry.setObjectId(treeEntry.getObjectId());
            entry.setPath(PathUtils.concat(treeEntity.getPath(), treeEntry.getName()));
            entries.add(entry);
            if (treeEntry.getType() == ObjectEntity.Type.blob) {
                entry.setType(ObjectEntity.Type.blob);
                entries.add(entry);
            }
            if (treeEntry.getType() == ObjectEntity.Type.tree) {
                entry.setType(ObjectEntity.Type.tree);
                walkTreeAndBlob(entry, objectManager, entries);
            }
        }
    }


    private static void putBlobCache(String commitObjectId, List<Entry> entries){
        if (commitObjectId2BlobEntriesCache.size() > CACHE_SIZE){
            commitObjectId2BlobEntriesCache.entrySet().iterator().remove();
        }
        commitObjectId2BlobEntriesCache.put(commitObjectId, entries);
    }

    private static List<Entry> getBlobFromCache(String commitObjectId){
        List<Entry> fromBlobCache = commitObjectId2BlobEntriesCache.get(commitObjectId);
        if (fromBlobCache == null){
            return collectBlob(getBlobAndTreeFromCache(commitObjectId));
        }
        return new ArrayList<>(fromBlobCache);
    }

    private static void putBlobAndTreeCache(String commitObjectId, List<Entry> entries){
        if (commitObjectId2BlobAndTreeEntriesCache.size() > CACHE_SIZE){
            commitObjectId2BlobAndTreeEntriesCache.entrySet().iterator().remove();
        }
        commitObjectId2BlobAndTreeEntriesCache.put(commitObjectId, entries);
    }

    private static List<Entry> getBlobAndTreeFromCache(String commitObjectId){
        List<Entry> fromBlobAndTreeCache = commitObjectId2BlobAndTreeEntriesCache.get(commitObjectId);
        if (fromBlobAndTreeCache == null){
            return null;
        }
        return new ArrayList<>(fromBlobAndTreeCache);
    }

    private static List<Entry> collectBlob(List<Entry> blobAndTrees){
        if (blobAndTrees == null){
            return null;
        }else{
            return blobAndTrees.stream().filter(x -> x.getType() == ObjectEntity.Type.blob).collect(Collectors.toList());
        }
    }
}
