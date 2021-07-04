package com.beyond.jgit.deprecated;

import com.beyond.jgit.GitLiteConfig;
import com.beyond.jgit.index.Index;
import com.beyond.jgit.index.IndexManager;
import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.object.ObjectManager;
import com.beyond.jgit.object.data.BlobObjectData;
import com.beyond.jgit.object.data.CommitObjectData;
import com.beyond.jgit.object.data.TreeObjectData;
import com.beyond.jgit.util.ObjectUtils;
import com.beyond.jgit.util.PathUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.util.*;

// deprecated for backup
@Deprecated
@Slf4j
public class GitLite {

    private final ObjectManager objectManager;
    private final IndexManager indexManager;

    private final GitLiteConfig config;

    public GitLite(GitLiteConfig config) {
        this.config = config;
        this.objectManager = new ObjectManager(config.getObjectsDir());
        this.indexManager = new IndexManager(config.getIndexPath());
    }

    public void add(String... paths) throws IOException {
        List<File> files = new ArrayList<>();
        if (paths.length ==0){
            Collection<File> listFiles =  FileUtils.listFilesAndDirs(new File(config.getLocalDir()),TrueFileFilter.INSTANCE, new IOFileFilter() {
                @Override
                public boolean accept(File file) {
                    return !".git".equals(file.getName());
                }

                @Override
                public boolean accept(File dir, String name) {
                    return false;
                }
            });
            files.addAll(listFiles);
        }else{
            for (String path : paths) {
                Collection<File> listFiles =  FileUtils.listFilesAndDirs(new File(config.getLocalDir(), path),TrueFileFilter.INSTANCE, new IOFileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return !".git".equals(file.getName());
                    }

                    @Override
                    public boolean accept(File dir, String name) {
                        return false;
                    }
                });
                files.addAll(listFiles);
            }
        }

        Index index = new Index();
        for (File file : Objects.requireNonNull(files)) {
            if (file.isFile()) {
                ObjectEntity objectEntity = buildBlob(file);
                String objectId = ObjectUtils.sha1hash(objectEntity);
                Index.Entry entry = new Index.Entry();
                entry.setPath(PathUtils.getRelativePath(config.getLocalDir(), file.getAbsolutePath()));
                entry.setObjectId(objectId);
                index.getEntries().add(entry);
            }
        }
        indexManager.save(index);
    }


    public String commit() throws IOException {
        ObjectEntity tree = addTreeFromIndex(IndexManager.parseIndex(config.getIndexPath()));
        ObjectEntity commit = addCommitObject(tree);
        return new String(commit.getData());
    }

    @Deprecated
    private ObjectEntity buildTree(File dir) throws IOException {
        File[] files = dir.listFiles((dir1, name) -> !".git".equals(name));
        TreeObjectData treeObjectData = new TreeObjectData();
        List<TreeObjectData.TreeEntry> entries = treeObjectData.getEntries();
        for (File file : Objects.requireNonNull(files)) {
            ObjectEntity childTreeEntity = null;
            if (file.isDirectory()) {
                childTreeEntity = buildTree(file);
            }
            if (file.isFile()) {
                childTreeEntity = buildBlob(file);
            }
            if (childTreeEntity == null) {
                continue;
            }
            TreeObjectData.TreeEntry treeEntry = new TreeObjectData.TreeEntry();
            treeEntry.setType(childTreeEntity.getType());
            treeEntry.setName(file.getName());
            treeEntry.setMode(ObjectUtils.getModeByType(childTreeEntity.getType()));
            treeEntry.setObjectId(ObjectUtils.sha1hash(childTreeEntity.getType(), childTreeEntity.getData()));
            entries.add(treeEntry);
        }
        ObjectEntity objectEntity = new ObjectEntity();
        objectEntity.setType(ObjectEntity.Type.tree);
        entries.sort(Comparator.comparing(TreeObjectData.TreeEntry::getName));
        objectEntity.setData(treeObjectData.toBytes());
        String objectId = objectManager.write(objectEntity);
        log.debug(dir.getName() + " " + objectId);
        log.debug(dir.getName() + ":{}", TreeObjectData.parseFrom(objectEntity.getData()).toString());
        return objectEntity;
    }

    private ObjectEntity buildBlob(File file) throws IOException {
        BlobObjectData blobObjectData = new BlobObjectData();
        blobObjectData.setData(FileUtils.readFileToByteArray(file));

        ObjectEntity objectEntity = new ObjectEntity();
        objectEntity.setType(ObjectEntity.Type.blob);
        objectEntity.setData(blobObjectData.toBytes());
        String objectId = objectManager.write(objectEntity);
        log.debug(file.getName() + " " + objectId);
        return objectEntity;
    }

    @Deprecated
    private ObjectEntity buildCommit(File root) throws IOException {
        ObjectEntity objectEntity = buildTree(root);

        CommitObjectData commitObjectData = new CommitObjectData();
        commitObjectData.setTree(ObjectUtils.sha1hash(objectEntity.getType(), objectEntity.getData()));
        commitObjectData.setCommitTime(1620313388000L);
        CommitObjectData.User user = new CommitObjectData.User();
        user.setName("beyondlov1");
        user.setEmail("beyondlov1@hotmail.com");
        commitObjectData.setCommitter(user);
        commitObjectData.setAuthor(user);
        commitObjectData.setMessage("a");
        commitObjectData.addParent("463783ca68ec49b4630ffbf5c35264461a3b2174"); // fixme: 找上一个提交

        ObjectEntity commitObjectEntity = new ObjectEntity();
        commitObjectEntity.setType(ObjectEntity.Type.commit);
        commitObjectEntity.setData(commitObjectData.toBytes());

        String commitObjectId = objectManager.write(commitObjectEntity);
        log.debug("commitObjectId: {}", commitObjectId);

        return commitObjectEntity;
    }

    private ObjectEntity addCommitObject(ObjectEntity tree) throws IOException {
        CommitObjectData commitObjectData = new CommitObjectData();
        commitObjectData.setTree(ObjectUtils.sha1hash(tree));
        commitObjectData.setCommitTime(1620313388000L);
        CommitObjectData.User user = new CommitObjectData.User();
        user.setName(config.getCommitterName());
        user.setEmail(config.getCommitterEmail());
        commitObjectData.setCommitter(user);
        commitObjectData.setAuthor(user);
        commitObjectData.setMessage("a");
        commitObjectData.addParent("463783ca68ec49b4630ffbf5c35264461a3b2174"); // fixme: 找上一个提交

        ObjectEntity commitObjectEntity = new ObjectEntity();
        commitObjectEntity.setType(ObjectEntity.Type.commit);
        commitObjectEntity.setData(commitObjectData.toBytes());

        String commitObjectId = objectManager.write(commitObjectEntity);
        log.debug("commitObjectId: {}", commitObjectId);

        return commitObjectEntity;
    }

    private ObjectEntity addTreeFromIndex(Index index) throws IOException {

        // collect nodes
        Map<File, FileNode> nodes = new HashMap<>();
        File rootFile = new File(config.getLocalDir());
        FileNode root = new FileNode(rootFile);
        nodes.put(rootFile, root);
        for (Index.Entry entry : index.getEntries()) {
            File file = new File(config.getLocalDir(), entry.getPath());
            FileNode fileNode = new FileNode(file);
            fileNode.setObjectId(entry.getObjectId());
            walkUp(fileNode, root, nodes);
        }

        // create tree
        for (FileNode node : nodes.values()) {
            File parentFile = node.getFile().getParentFile();
            FileNode parentNode = nodes.get(parentFile);
            if (parentNode != null){
                parentNode.addChild(node);
            }
        }

        return addTreeObject(root);
    }

    private void walkUp(FileNode fileNode, FileNode rootNode, Map<File, FileNode> nodes){
        File file = fileNode.getFile();
        File root = rootNode.getFile();
        if (Objects.equals(file, root)){
            return;
        }
        if (nodes.containsKey(file)){
            return;
        }
        File parentFile = file.getParentFile();
        nodes.put(file,fileNode);
        walkUp(new FileNode(parentFile), rootNode, nodes);
    }

    private ObjectEntity addTreeObject(FileNode fileNode) throws IOException {
        List<FileNode> children = fileNode.getChildren();
        TreeObjectData treeObjectData = new TreeObjectData();
        List<TreeObjectData.TreeEntry> entries = treeObjectData.getEntries();
        for (FileNode child : children) {
            if (child.getType() == ObjectEntity.Type.tree) {
                addTreeObject(child);
            }
            TreeObjectData.TreeEntry treeEntry = new TreeObjectData.TreeEntry();
            treeEntry.setType(child.getType());
            treeEntry.setName(child.getFileName());
            treeEntry.setMode(ObjectUtils.getModeByType(child.getType()));
            treeEntry.setObjectId(child.getObjectId());
            entries.add(treeEntry);
        }
        ObjectEntity objectEntity = new ObjectEntity();
        objectEntity.setType(ObjectEntity.Type.tree);
        entries.sort(Comparator.comparing(TreeObjectData.TreeEntry::getName));
        objectEntity.setData(treeObjectData.toBytes());
        String objectId = objectManager.write(objectEntity);
        fileNode.setObjectId(objectId);
        log.debug(fileNode.getFileName()+":{}", objectId);
        return objectEntity;
    }


    public void fetch() {
        // todo easy
    }

    public void merge() {
        // todo hard
        // 找不同
        // 1. 创建目录结构列表
        // 2. 对比基础commit和新commit之间的文件路径差异(列出那些文件是添加， 那些是删除， 那些是更新)
        // 3. local和remote找到共同的提交历史， 对比变化
        // 4. 对比变化的差异（以文件路径为key)
    }

    public static void push() {
        // todo hard
    }

    @Data
    private static class FileNode{
        private File file;
        private List<FileNode> children = new ArrayList<>();
        private String objectId;
        private ObjectEntity.Type type;

        public FileNode(File file) {
            this.file = file;
            if (file.isDirectory()){
                type = ObjectEntity.Type.tree;
            }
            if (file.isFile()){
                type = ObjectEntity.Type.blob;
            }
        }

        void addChild(FileNode fileNode){
            children.add(fileNode);
        }

        String getFileName(){
            return file.getName();
        }
    }


    public static void main(String[] args) throws IOException {
        GitLiteConfig config = new GitLiteConfig();
        config.setLocalDir("/home/beyond/Documents/GitHubProject/test-jgit");
        config.setHeadPath("");
        config.setGitDir(PathUtils.concat("/home/beyond/Documents/tmp-git-2/", ".git"));
        config.setIndexPath(PathUtils.concat(config.getGitDir(), "index.json"));
        config.setObjectsDir(PathUtils.concat(config.getGitDir(), "objects"));
        config.setRefsDir(PathUtils.concat(config.getGitDir(), "refs"));
        config.setRefsRemotesDir(PathUtils.concat(config.getGitDir(), "refs","remotes"));
        config.setRefsHeadsDir(PathUtils.concat(config.getGitDir(), "refs","heads"));
        config.setCommitterName("beyondlov1");
        config.setCommitterEmail("beyondlov1@hotmail.com");

        GitLite gitLite = new GitLite(config);
        gitLite.add();
        System.out.println(gitLite.commit());;
    }
}
