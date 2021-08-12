package com.beyond.jgit;

import com.beyond.delta.DeltaUtils;
import com.beyond.jgit.ignore.IgnoreNode;
import com.beyond.jgit.index.Index;
import com.beyond.jgit.index.IndexDiffResult;
import com.beyond.jgit.index.IndexDiffer;
import com.beyond.jgit.index.IndexManager;
import com.beyond.jgit.log.LogItem;
import com.beyond.jgit.log.LogManager;
import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.object.ObjectManager;
import com.beyond.jgit.object.data.BlobObjectData;
import com.beyond.jgit.object.data.CommitObjectData;
import com.beyond.jgit.object.data.TreeObjectData;
import com.beyond.jgit.pack.*;
import com.beyond.jgit.storage.FileStorage;
import com.beyond.jgit.storage.SardineStorage;
import com.beyond.jgit.storage.Storage;
import com.beyond.jgit.storage.TransportMapping;
import com.beyond.jgit.util.*;
import com.beyond.jgit.util.commitchain.CommitChainItem;
import com.beyond.jgit.util.commitchain.CommitChainItemLazy;
import com.beyond.jgit.util.commitchain.CommitChainItemSingleParent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.beyond.jgit.util.commitchain.CommitChainUtils.*;

@SuppressWarnings("DuplicatedCode")
@Slf4j
public class GitLite {

    public static final String EMPTY_OBJECT_ID = "0000000000000000000000000000000000000000";

    private final ObjectManager objectManager;
    private final IndexManager indexManager;
    private final LogManager localLogManager;


    private final Map<String, LogManager> remoteLogManagerMap;
    private final Map<String, Storage> remoteStorageMap;

    private final GitLiteConfig config;

    public GitLite(GitLiteConfig config) {
        this.config = config;
        this.objectManager = new ObjectManager(config.getObjectsDir());
        this.indexManager = new IndexManager(config.getIndexPath());
        this.localLogManager = new LogManager(PathUtils.concat(config.getLogsHeadsDir(), "master.json"));

        this.remoteLogManagerMap = new HashMap<>();
        this.remoteStorageMap = new HashMap<>();

        for (GitLiteConfig.RemoteConfig remoteConfig : config.getRemoteConfigs()) {
            remoteLogManagerMap.put(remoteConfig.getRemoteName(), new LogManager(PathUtils.concat(config.getLogsRemotesDir(), remoteConfig.getRemoteName(), "master.json")));
            if (remoteConfig.getRemoteUrl().startsWith("http://") || remoteConfig.getRemoteUrl().startsWith("https://")) {
                if (StringUtils.isNotBlank(remoteConfig.getRemoteTmpDir())) {
                    remoteStorageMap.put(remoteConfig.getRemoteName(),
                            new SardineStorage(PathUtils.concat(remoteConfig.getRemoteUrl(), ".git"),
                                    remoteConfig.getRemoteUserName(),
                                    remoteConfig.getRemotePassword(),
                                    PathUtils.concat(remoteConfig.getRemoteTmpDir(), remoteConfig.getRemoteName(), "master.ed"),
                                    PathUtils.concat(remoteConfig.getRemoteTmpDir(), remoteConfig.getRemoteName(), "session")));
                } else {
                    remoteStorageMap.put(remoteConfig.getRemoteName(),
                            new SardineStorage(PathUtils.concat(remoteConfig.getRemoteUrl(), ".git"),
                                    remoteConfig.getRemoteUserName(),
                                    remoteConfig.getRemotePassword()));
                }
            } else {
                remoteStorageMap.put(remoteConfig.getRemoteName(), new FileStorage(PathUtils.concat(remoteConfig.getRemoteUrl(), ".git")));
            }
        }
    }

    public void init() throws IOException {
        mkdirIfNotExists(config.getLocalDir());
        mkdirIfNotExists(config.getGitDir());
        mkdirIfNotExists(config.getObjectsDir());
        mkdirIfNotExists(config.getLogsDir());
        mkdirIfNotExists(config.getRefsDir());
        String headPath = config.getHeadPath();
        File file = new File(headPath);
        if (!file.exists()) {
            FileUtils.write(file, "ref: refs/heads/master", StandardCharsets.UTF_8);
        }
        config.save();
    }

    private void mkdirIfNotExists(String path) {
        File file = new File(path);
        if (file.exists()) {
            return;
        }
        boolean mkdirs = file.mkdirs();
        if (!mkdirs) {
            throw new RuntimeException("mkdir fail");
        }
    }

    public void add(String... paths) throws IOException {
        List<File> files = new ArrayList<>();
        if (paths.length == 0) {
            Collection<File> listFiles = FileUtil.listFilesAndDirsWithoutNameOf(config.getLocalDir(), ".git");
            files.addAll(listFiles);
        } else {
            for (String path : paths) {
                Collection<File> listFiles = FileUtil.listFilesAndDirsWithoutNameOf(PathUtils.concat(config.getLocalDir(), path), ".git");
                files.addAll(listFiles);
            }
        }

        // 过滤 gitignore
        if (StringUtils.isNotBlank(config.getIgnorePath()) && new File(config.getIgnorePath()).exists()){
            IgnoreNode ignoreNode = IgnoreNode.load(config.getIgnorePath());
            File localDir = new File(config.getLocalDir());
            files.removeIf(x -> IgnoreNode.isIgnored(ignoreNode, x, localDir));
        }

        Index index = new Index();
        for (File file : Objects.requireNonNull(files)) {
            if (file.isFile()) {
                ObjectEntity objectEntity = addBlobObject(file);
                String objectId = ObjectUtils.sha1hash(objectEntity);
                Index.Entry entry = new Index.Entry();
                entry.setPath(PathUtils.getRelativePath(config.getLocalDir(), file.getAbsolutePath()));
                entry.setObjectId(objectId);
                index.getEntries().add(entry);
            }
        }
        indexManager.save(index);
    }

    public String commit(String message, String...parents) throws IOException {
        return commit(IndexManager.parseIndex(config.getIndexPath()), message,parents);
    }

    public String commit(Index index, String message, String...parents) throws IOException {

        Index committedIndex = Index.generateFromCommit(findLocalCommitObjectId(), objectManager);
        IndexDiffResult committedDiff = IndexDiffer.diff(index, committedIndex);
        if (!committedDiff.isChanged()) {
            log.debug("nothing changed, no commit");
            return "nothing changed";
        }

        ObjectEntity tree = addTreeFromIndex(index);
        ObjectEntity commit = addCommitObject(tree, message,parents);
        File headRefFile = getHeadRefFile();
        FileUtils.writeStringToFile(headRefFile, ObjectUtils.sha1hash(commit), StandardCharsets.UTF_8);

        // log
        CommitObjectData commitObjectData = CommitObjectData.parseFrom(commit.getData());
        localLogManager.lock();
        localLogManager.appendToLock(ObjectUtils.sha1hash(commit), config.getCommitterName(), config.getCommitterEmail(), "auto commit", commitObjectData.getCommitTime());
        localLogManager.commit();
        return new String(commit.getData());
    }

    private ObjectEntity addBlobObject(File file) throws IOException {
        BlobObjectData blobObjectData = new BlobObjectData();
        blobObjectData.setData(FileUtils.readFileToByteArray(file));

        ObjectEntity objectEntity = new ObjectEntity();
        objectEntity.setType(ObjectEntity.Type.blob);
        objectEntity.setData(blobObjectData.toBytes());
        String objectId = objectManager.write(objectEntity);
        log.debug(file.getName() + " " + objectId);
        return objectEntity;
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
            if (parentNode != null) {
                parentNode.addChild(node);
            }
        }

        return addTreeObject(root);
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
        log.debug(fileNode.getFileName() + ":{}", objectId);
        return objectEntity;
    }

    private void walkUp(FileNode fileNode, FileNode rootNode, Map<File, FileNode> nodes) {
        File file = fileNode.getFile();
        File root = rootNode.getFile();
        if (Objects.equals(file, root)) {
            return;
        }
        if (nodes.containsKey(file)) {
            return;
        }
        File parentFile = file.getParentFile();
        nodes.put(file, fileNode);
        walkUp(new FileNode(parentFile), rootNode, nodes);
    }

    private ObjectEntity addCommitObject(ObjectEntity tree, String message, String... parents) throws IOException {
        CommitObjectData commitObjectData = new CommitObjectData();
        commitObjectData.setTree(ObjectUtils.sha1hash(tree));
        commitObjectData.setCommitTime(System.currentTimeMillis());
        CommitObjectData.User user = new CommitObjectData.User();
        user.setName(config.getCommitterName());
        user.setEmail(config.getCommitterEmail());
        commitObjectData.setCommitter(user);
        commitObjectData.setAuthor(user);
        commitObjectData.setMessage(message);
        if (ArrayUtils.isEmpty(parents)){
            String localCommitObjectId = findLocalCommitObjectId();
            if (localCommitObjectId == null){
                commitObjectData.addParent(EMPTY_OBJECT_ID);
            }else {
                commitObjectData.addParent(localCommitObjectId);
            }
        }else {
            for (String parent : parents) {
                commitObjectData.addParent(parent);
            }
        }
        ObjectEntity commitObjectEntity = new ObjectEntity();
        commitObjectEntity.setType(ObjectEntity.Type.commit);
        commitObjectEntity.setData(commitObjectData.toBytes());

        String commitObjectId = objectManager.write(commitObjectEntity);
        log.debug("commitObjectId: {}", commitObjectId);

        return commitObjectEntity;
    }


    public void clone(String remoteName) throws IOException {
        Storage remoteStorage = remoteStorageMap.get(remoteName);
        if (remoteStorage == null) {
            throw new RuntimeException("remoteStorage is not exist");
        }
        LogManager remoteLogManager = remoteLogManagerMap.get(remoteName);
        if (remoteLogManager == null) {
            throw new RuntimeException("remoteLogManager is not exist");
        }
        if (!remoteStorage.exists(PathUtils.concat("refs", "remotes", remoteName, "master"))) {
            log.warn("remote is empty");
            log.warn("deleting dirty remote refs ...");
            FileUtils.deleteQuietly(new File(PathUtils.concat(config.getRefsRemotesDir(), remoteName, "master")));
            log.warn("deleted dirty remote refs ...");
            return;
        }

        init();
        initRemoteDirs(remoteName);

        // fetch remote head to remote head lock
        // locked?
        File remoteHeadFile = new File(PathUtils.concat(config.getRefsRemotesDir(), remoteName, "master"));
        remoteStorage.download(PathUtils.concat("refs", "remotes", remoteName, "master"), remoteHeadFile);

        // clone packs
        fetchPacks(remoteName);

        // 根据 remote head 判断需要下载那些objects
        String webRemoteLatestCommitObjectId = findRemoteCommitObjectId(remoteName);
        CommitChainItem chainHead = getRemoteCommitChainHead(webRemoteLatestCommitObjectId, null, remoteStorage);
        chainHead.dfsWalk(commitChainItem -> {
            // olderCommitObjectId的parents是空的， 这里不需要再重新下载一次olderCommitObjectId
            if (CollectionUtils.isNotEmpty(commitChainItem.getParents())) {
                downloadByObjectIdRecursive(commitChainItem.getCommitObjectId(), remoteStorage);
            }
        });

        ObjectEntity commitObjectEntity = objectManager.read(webRemoteLatestCommitObjectId);
        CommitObjectData commitObjectData = CommitObjectData.parseFrom(commitObjectEntity.getData());

        LogItem logItem = new LogItem();
        logItem.setParentCommitObjectId(EMPTY_OBJECT_ID);
        logItem.setCommitObjectId(webRemoteLatestCommitObjectId);
        logItem.setCommitterName(commitObjectData.getCommitter().getName());
        logItem.setCommitterEmail(commitObjectData.getCommitter().getEmail());
        logItem.setMessage("clone");
        logItem.setMtime(System.currentTimeMillis());

        File refsHeadLockFile = new File(PathUtils.concat(config.getRefsHeadsDir(), "master.lock"));
        try {
            remoteLogManager.lock();
            remoteLogManager.writeToLock(Collections.singletonList(logItem));
            localLogManager.lock();
            localLogManager.writeToLock(Collections.singletonList(logItem));
            FileUtils.write(refsHeadLockFile, webRemoteLatestCommitObjectId, StandardCharsets.UTF_8);

            remoteLogManager.commit();
            localLogManager.commit();
            Files.move(refsHeadLockFile.toPath(), new File(PathUtils.concat(config.getRefsHeadsDir(), "master")).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (Exception e) {
            log.error("clone fail", e);
            remoteLogManager.rollback();
            localLogManager.rollback();
            FileUtils.deleteQuietly(refsHeadLockFile);
            return;
        }

        checkout(webRemoteLatestCommitObjectId);
    }


    public void fetch(String remoteName) throws IOException {
        Storage remoteStorage = remoteStorageMap.get(remoteName);
        if (remoteStorage == null) {
            throw new RuntimeException("remoteStorage is not exist");
        }
        LogManager remoteLogManager = remoteLogManagerMap.get(remoteName);
        if (remoteLogManager == null) {
            throw new RuntimeException("remoteLogManager is not exist");
        }
        if (!remoteStorage.exists(PathUtils.concat("refs", "remotes", remoteName, "master"))) {
            log.warn("remote is empty");
            log.warn("deleting dirty remote refs ...");
            FileUtils.deleteQuietly(new File(PathUtils.concat(config.getRefsRemotesDir(), remoteName, "master")));
            log.warn("deleted dirty remote refs ...");
            return;
        }

        initRemoteDirs(remoteName);

        // fetch remote head to remote head lock
        // locked?
        File remoteHeadFile = new File(PathUtils.concat(config.getRefsRemotesDir(), remoteName, "master"));
        File remoteHeadLockFile = new File(PathUtils.concat(config.getRefsRemotesDir(), remoteName, "master.lock"));
        if (remoteHeadLockFile.exists()) {
            log.error("remote master is locked, path:{}", remoteHeadLockFile.getAbsolutePath());
            throw new RuntimeException("remote master is locked");
        }
        remoteStorage.download(PathUtils.concat("refs", "remotes", remoteName, "master"), remoteHeadLockFile);

        List<LogItem> logs = remoteLogManager.getLogs();
        if (remoteHeadFile.exists() && logs != null) {
            String remoteHeadObjectId = FileUtils.readFileToString(remoteHeadFile, StandardCharsets.UTF_8);
            String remoteHeadLockObjectId = FileUtils.readFileToString(remoteHeadLockFile, StandardCharsets.UTF_8);
            Set<String> remotePushedObjectIds = logs.stream().map(LogItem::getCommitObjectId).collect(Collectors.toSet());
            if (Objects.equals(remoteHeadObjectId, remoteHeadLockObjectId) || remotePushedObjectIds.contains(remoteHeadLockObjectId)) {
                log.info("Already up to date.");
                Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            }
        }

        // 下载packs
        fetchPacks(remoteName);

        // 根据 remote head 判断需要下载那些objects
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);
        String remoteLockCommitObjectId = findRemoteLockCommitObjectId(remoteName);
        if (!remoteHeadFile.exists() || logs == null) {
            log.warn("local/remote log is empty, no fetch");
            Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }
        // 去remoteLog, remoteLog只在本地存,不上传. 改用commitObject中的parent获取提交链
        CommitChainItem chainHead = getRemoteCommitChainHead(remoteLockCommitObjectId, remoteCommitObjectId, remoteStorage);
        chainHead.dfsWalk(commitChainItem -> {
            // olderCommitObjectId的parents是空的， 这里不需要再重新下载一次olderCommitObjectId
            if (CollectionUtils.isNotEmpty(commitChainItem.getParents())) {
                downloadByObjectIdRecursive(commitChainItem.getCommitObjectId(), remoteStorage);
            }
        });


        // update head
        Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

    }

    /**
     * 下载packs
     */
    private void fetchPacks(String remoteName) throws IOException {
        Storage remoteStorage = remoteStorageMap.get(remoteName);
        String remotePackInfoPath = PathUtils.concat("objects", "info", "packs");
        File packInfoFileTmp = new File(config.getObjectInfoDir(), "packs.tmp");
        if (remoteStorage.exists(remotePackInfoPath)) {
            remoteStorage.download(remotePackInfoPath, packInfoFileTmp);
        }
        String packInfoStr = FileUtils.readFileToString(packInfoFileTmp, StandardCharsets.UTF_8);
        PackInfo packInfo = JsonUtils.readValue(packInfoStr, PackInfo.class);
        if (packInfo!=null){
            for (PackInfo.Item item : packInfo.getItems()) {
                String remotePackPath = PathUtils.concat("objects", "pack", item.getName());
                String localPackPath = PathUtils.concat(config.getObjectPackDir(), item.getName());
                remoteStorage.download(remotePackPath, new File(localPackPath));
                remoteStorage.download(PackUtils.getIndexPath(remotePackPath), new File(PackUtils.getIndexPath(localPackPath)));
            }
        }

        File packInfoFile = new File(config.getObjectInfoDir(), "packs");
        FileUtil.move(packInfoFileTmp, packInfoFile);
    }


    // 包新不包旧
    private CommitChainItem getRemoteCommitChainHead(String newerCommitObjectId, String olderCommitObjectId, Storage remoteStorage) throws IOException {
        downloadCommitObjectsBetween(newerCommitObjectId, olderCommitObjectId, remoteStorage);
        return getCommitChainHead(newerCommitObjectId, olderCommitObjectId, objectManager);
    }

    private void downloadCommitObjectsBetween(String newerCommitObjectId, String olderCommitObjectId, Storage remoteStorage) throws IOException {
        if (Objects.equals(newerCommitObjectId, olderCommitObjectId)) {
            return;
        }
        if (Objects.equals(newerCommitObjectId, EMPTY_OBJECT_ID)) {
            return;
        }

        if (!objectManager.exists(newerCommitObjectId)) {
            File objectFile = ObjectUtils.getObjectFile(config.getObjectsDir(), newerCommitObjectId);
            FileUtils.forceMkdirParent(objectFile);
            remoteStorage.download(PathUtils.concat("objects", ObjectUtils.path(newerCommitObjectId)), objectFile);
        }
        ObjectEntity commitObjectEntity = objectManager.read(newerCommitObjectId);
        List<String> parents = CommitObjectData.parseFrom(commitObjectEntity.getData()).getParents();
        //  merge时会有多个
        for (String parent : parents) {
            downloadCommitObjectsBetween(parent, olderCommitObjectId, remoteStorage);
        }
    }

    private void downloadByObjectIdRecursive(String objectId, Storage remoteStorage) throws IOException {
        if (!objectManager.exists(objectId)) {
            File objectFile = ObjectUtils.getObjectFile(config.getObjectsDir(), objectId);
            FileUtils.forceMkdirParent(objectFile);
            remoteStorage.download(PathUtils.concat("objects", ObjectUtils.path(objectId)), objectFile);
        }
        ObjectEntity objectEntity = objectManager.read(objectId);
        switch (objectEntity.getType()) {
            case commit:
                CommitObjectData commitObjectData = CommitObjectData.parseFrom(objectEntity.getData());
                String tree = commitObjectData.getTree();
                downloadByObjectIdRecursive(tree, remoteStorage);
                break;
            case tree:
                TreeObjectData treeObjectData = TreeObjectData.parseFrom(objectEntity.getData());
                List<TreeObjectData.TreeEntry> entries = treeObjectData.getEntries();
                for (TreeObjectData.TreeEntry entry : entries) {
                    downloadByObjectIdRecursive(entry.getObjectId(), remoteStorage);
                }
                break;
            case blob:
                // do nothing
                break;
            default:
                throw new RuntimeException("type error");
        }

    }

    public void checkout() throws IOException {
        checkout(findLocalCommitObjectId());
    }

    public void checkout(String commitObjectId) throws IOException {
        Index targetIndex = Index.generateFromCommit(commitObjectId, objectManager);
        Index localIndex = Index.generateFromLocalDir(config.getLocalDir());

        IndexDiffResult diff = IndexDiffer.diff(targetIndex, localIndex);
        Set<Index.Entry> removed = diff.getRemoved();
        for (Index.Entry entry : removed) {
            FileUtils.deleteQuietly(new File(PathUtils.concat(config.getLocalDir(), entry.getPath())));
        }

        List<Index.Entry> changedEntries = new ArrayList<>();
        changedEntries.addAll(diff.getAdded());
        changedEntries.addAll(diff.getUpdated());
        for (Index.Entry entry : changedEntries) {
            String absPath = PathUtils.concat(config.getLocalDir(), entry.getPath());
            ObjectEntity objectEntity = objectManager.read(entry.getObjectId());
            if (objectEntity.getType() == ObjectEntity.Type.blob) {
                BlobObjectData blobObjectData = BlobObjectData.parseFrom(objectEntity.getData());
                FileUtils.writeByteArrayToFile(new File(absPath), blobObjectData.getData());
            }
        }

        Index index = Index.generateFromCommit(commitObjectId, objectManager);
        indexManager.save(index);
    }


    /**
     * 把remote的更改merge到本地commit中
     */
    public void merge(String remoteName) throws IOException {
        // 找不同
        // 1. 创建目录结构列表
        // 2. 对比基础commit和新commit之间的文件路径差异(列出那些文件是添加， 那些是删除， 那些是更新)
        // 3. local和remote找到共同的提交历史， 对比变化
        // 4. 对比变化的差异（以文件路径为key)
        // 根据log查询local和remote共同经历的最后一个commit


        String localCommitObjectId = findLocalCommitObjectId();
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);

        // region debug
        if (StringUtils.isNotBlank(localCommitObjectId)){
            CommitChainItem localCommitChainRoot = getCommitChainHead(localCommitObjectId, EMPTY_OBJECT_ID, objectManager);
            localCommitChainRoot.print();
        }
        if (StringUtils.isNotBlank(remoteCommitObjectId)) {
            CommitChainItem remoteCommitChainRoot = getCommitChainHead(remoteCommitObjectId, EMPTY_OBJECT_ID, objectManager);
            remoteCommitChainRoot.print();
        }
        // endregion

        if (StringUtils.equals(localCommitObjectId, remoteCommitObjectId)) {
            log.info("nothing changed, no merge");
            return;
        }

        String intersectionCommitObjectId = null;

        if (localCommitObjectId != null && remoteCommitObjectId != null) {
            Set<String> localCommitObjectIds = new HashSet<>();
            localCommitObjectIds.add(localCommitObjectId);
            Set<String> remoteCommitObjectIds = new HashSet<>();
            remoteCommitObjectIds.add(remoteCommitObjectId);

            List<CommitChainItemLazy> localCommitChainItemLazys = new ArrayList<>();
            localCommitChainItemLazys.add(new CommitChainItemLazy(localCommitObjectId, objectManager));
            List<CommitChainItemLazy> remoteCommitChainItemLazys = new ArrayList<>();
            remoteCommitChainItemLazys.add(new CommitChainItemLazy(remoteCommitObjectId, objectManager));
            for (; ; ) {
                List<CommitChainItemLazy> newLocalCommitChainItemLazys = new ArrayList<>();
                for (CommitChainItemLazy localLazy : localCommitChainItemLazys) {
                    Set<String> localParents = localLazy.getParents().stream().map(CommitChainItem::getCommitObjectId).collect(Collectors.toSet());
                    for (String localParent : localParents) {
                        if (remoteCommitObjectIds.contains(localParent)) {
                            intersectionCommitObjectId = localParent;
                            break;
                        }
                        localCommitObjectIds.add(localParent);
                        newLocalCommitChainItemLazys.add(new CommitChainItemLazy(localParent, objectManager));
                    }
                    if (intersectionCommitObjectId != null){
                        break;
                    }
                }
                localCommitChainItemLazys = newLocalCommitChainItemLazys;

                List<CommitChainItemLazy> newRemoteCommitChainItemLazys = new ArrayList<>();
                for (CommitChainItemLazy remoteLazy : remoteCommitChainItemLazys) {
                    Set<String> remoteParents = remoteLazy.getParents().stream().map(CommitChainItem::getCommitObjectId).collect(Collectors.toSet());
                    for (String remoteParent : remoteParents) {
                        if (localCommitObjectIds.contains(remoteParent)) {
                            intersectionCommitObjectId = remoteParent;
                            break;
                        }
                        remoteCommitObjectIds.add(remoteParent);
                        newRemoteCommitChainItemLazys.add(new CommitChainItemLazy(remoteParent, objectManager));
                    }
                    if (intersectionCommitObjectId != null){
                        break;
                    }
                }
                remoteCommitChainItemLazys = newRemoteCommitChainItemLazys;

                if (intersectionCommitObjectId != null){
                    break;
                }

                if (CollectionUtils.isEmpty(localCommitChainItemLazys) && CollectionUtils.isEmpty(remoteCommitChainItemLazys)) {
                    break;
                }
            }
        }

        if (intersectionCommitObjectId == null) {
            log.warn("no intersectionCommitObjectId, remote log is empty, cover.");
        }

        Index intersectionIndex = Index.generateFromCommit(intersectionCommitObjectId, objectManager);

        Index committedHeadIndex = Index.generateFromCommit(localCommitObjectId, objectManager);
        IndexDiffResult committedDiff = IndexDiffer.diff(committedHeadIndex, intersectionIndex);
        log.debug("committedDiff: {}", JsonUtils.writeValueAsString(committedDiff));

        Index remoteHeadIndex = Index.generateFromCommit(remoteCommitObjectId, objectManager);
        IndexDiffResult remoteDiff = IndexDiffer.diff(remoteHeadIndex, intersectionIndex);
        log.debug("remoteDiff: {}", JsonUtils.writeValueAsString(remoteDiff));

        // - 没变化直接用变化的commit - 两边都有变化, 更新index, 根据 index 新建commit

        Set<Index.Entry> committedChanged = new HashSet<>();
        committedChanged.addAll(committedDiff.getAdded());
        committedChanged.addAll(committedDiff.getUpdated());
        committedChanged.addAll(committedDiff.getRemoved());

        Set<Index.Entry> remoteChanged = new HashSet<>();
        remoteChanged.addAll(remoteDiff.getAdded());
        remoteChanged.addAll(remoteDiff.getUpdated());
        remoteChanged.addAll(remoteDiff.getRemoved());

        if (!committedDiff.isChanged() && !remoteDiff.isChanged()) {
            log.debug("nothing changed, no merge");
            return;
        }

        Map<String, String> committedChangedPath2ObjectIdMap = committedChanged.stream().collect(Collectors.toMap(Index.Entry::getPath, Index.Entry::getObjectId));
        Map<String, String> remoteChangedPath2ObjectIdMap = remoteChanged.stream().collect(Collectors.toMap(Index.Entry::getPath, Index.Entry::getObjectId));

        Collection<String> intersection = CollectionUtils.intersection(committedChangedPath2ObjectIdMap.keySet(), remoteChangedPath2ObjectIdMap.keySet());
        intersection.removeIf(x -> Objects.equals(committedChangedPath2ObjectIdMap.get(x), remoteChangedPath2ObjectIdMap.get(x)));

        if (CollectionUtils.isNotEmpty(intersection)) {
            // todo: merge conflicted
            throw new RuntimeException("not supported yet");
        }

        if (committedHeadIndex == null) {
            committedHeadIndex = new Index();
        }
        committedHeadIndex.upsert(remoteDiff.getAdded());
        committedHeadIndex.upsert(remoteDiff.getUpdated());
        committedHeadIndex.remove(remoteDiff.getRemoved());

        if (remoteHeadIndex == null) {
            remoteHeadIndex = new Index();
        }
        remoteHeadIndex.upsert(committedDiff.getAdded());
        remoteHeadIndex.upsert(committedDiff.getUpdated());
        remoteHeadIndex.remove(committedDiff.getRemoved());

        log.debug(JsonUtils.writeValueAsString(committedHeadIndex));
        log.debug(JsonUtils.writeValueAsString(remoteHeadIndex));

        indexManager.save(committedHeadIndex);

        if (!committedDiff.isChanged()){
            // 如果本地没有变化，只有远程变化, 则只改HEAD
            File headRefFile = getHeadRefFile();
            FileUtils.writeStringToFile(headRefFile, remoteCommitObjectId, StandardCharsets.UTF_8);
        }else{
            log.info("create merge commit");
            // 如果两个local和remote都有变化则merge
            commit("merge",localCommitObjectId, remoteCommitObjectId);
            log.info("merge committed");
        }

    }


    public String findLocalCommitObjectId() throws IOException {
        File file = getHeadRefFile();
        if (!file.exists()) {
            return null;
        }
        return StringUtils.trim(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    private File getHeadRefFile() throws IOException {
        String headPath = config.getHeadPath();
        String ref = FileUtils.readFileToString(new File(headPath), StandardCharsets.UTF_8);
        String relativePath = StringUtils.trim(StringUtils.substringAfter(ref, "ref: "));
        return new File(config.getGitDir(), relativePath);
    }

    public String findRemoteCommitObjectId(String remoteName) throws IOException {
        String headPath = config.getHeadPath();
        String ref = FileUtils.readFileToString(new File(headPath), StandardCharsets.UTF_8);
        String relativePath = StringUtils.trim(StringUtils.substringAfter(ref, "ref: "));
        relativePath = relativePath.replace(File.separator + "heads" + File.separator, File.separator + "remotes" + File.separator + remoteName + File.separator);
        File file = new File(config.getGitDir(), relativePath);
        if (!file.exists()) {
            return null;
        }
        return StringUtils.trim(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    private String findRemoteLockCommitObjectId(String remoteName) throws IOException {
        String headPath = config.getHeadPath();
        String ref = FileUtils.readFileToString(new File(headPath), StandardCharsets.UTF_8);
        String relativePath = StringUtils.trim(StringUtils.substringAfter(ref, "ref: "));
        relativePath = relativePath.replace(File.separator + "heads" + File.separator, File.separator + "remotes" + File.separator + remoteName + File.separator);
        File file = new File(config.getGitDir(), relativePath + ".lock");
        if (!file.exists()) {
            return null;
        }
        return StringUtils.trim(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    /**
     * 改为使用packAndPush
     * @param remoteName
     * @throws IOException
     */
    @Deprecated
    public void push(String remoteName) throws IOException {
        // todo: 检查远程的是否与本地冲突?

        Storage remoteStorage = remoteStorageMap.get(remoteName);
        if (remoteStorage == null) {
            throw new RuntimeException("remoteStorage is not exist");
        }
        LogManager remoteLogManager = remoteLogManagerMap.get(remoteName);
        if (remoteLogManager == null) {
            throw new RuntimeException("remoteLogManager is not exist");
        }

        initRemoteDirs(remoteName);

        // fetch哪些就push哪些
        // 1. 根据commit链, 将所有提交导致的变化objectId全部上传
        String localCommitObjectId = findLocalCommitObjectId();
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);

        if (Objects.equals(localCommitObjectId, remoteCommitObjectId)) {
            log.info("nothing changed, no push");
            return;
        }

        // 检查本地的remote和远程的remote是否有异常, 比如远程的文件被修改了导致本地的晚于远程
//        checkWebRemoteStatus(remoteName, remoteStorage, remoteLogManager);


        // fixme: clone 之后这里找不到parent的object, clone的时候要下载所有objects? 上传时再上传一个压缩包？
        CommitChainItem chainHead = getCommitChainHead(localCommitObjectId, remoteCommitObjectId, objectManager);
        List<List<CommitChainItem>> chains = getChainPaths(chainHead);
        List<List<CommitChainItemSingleParent>> singleParentChainPaths = pathsToSingleParentCommitChains(chains);
        // 去掉不包含remoteCommitObjectId的路径
        singleParentChainPaths.removeIf(x -> {
            Set<String> chainCommitObjectIds = x.stream().map(CommitChainItemSingleParent::getCommitObjectId).collect(Collectors.toSet());
            return remoteCommitObjectId != null && !chainCommitObjectIds.contains(remoteCommitObjectId);
        });
        // 去掉最后一个，即去掉remoteCommitObjectId
        for (List<CommitChainItemSingleParent> singleParentChainPath : singleParentChainPaths) {
            singleParentChainPath.removeIf(x -> x.getParent() == null);
        }

        IndexDiffResult combinedDiff = new IndexDiffResult();
        for (List<CommitChainItemSingleParent> chainPath : singleParentChainPaths) {
            for (CommitChainItemSingleParent commitChainItem : chainPath) {
                Index thisIndex = Index.generateFromCommit(commitChainItem.getCommitObjectId(), objectManager);
                Index parentIndex;
                if (commitChainItem.getParent() == null || Objects.equals(commitChainItem.getParent().getCommitObjectId(), EMPTY_OBJECT_ID)) {
                    parentIndex = null;
                } else {
                    parentIndex = Index.generateFromCommit(commitChainItem.getParent().getCommitObjectId(), objectManager);
                }
                IndexDiffResult committedDiff = IndexDiffer.diff(thisIndex, parentIndex);
                log.debug("committedDiff to push: {}", JsonUtils.writeValueAsString(committedDiff));

                // 2. 上传objects
                combinedDiff.getAdded().addAll(committedDiff.getAdded());
                combinedDiff.getUpdated().addAll(committedDiff.getUpdated());
                combinedDiff.getRemoved().addAll(committedDiff.getRemoved());
            }
        }

        if (!combinedDiff.isChanged()) {
            log.info("nothing changed, no push");
            return;
        }

        Set<Index.Entry> changedEntries = new HashSet<>();
        changedEntries.addAll(combinedDiff.getAdded());
        changedEntries.addAll(combinedDiff.getUpdated());

        //  upload
        List<String> objectIdsToUpload = new ArrayList<>();
        Set<String> dirs = changedEntries.stream().map(x -> PathUtils.parent(ObjectUtils.path(x.getObjectId()))).map(x -> PathUtils.concat("objects", x)).collect(Collectors.toSet());
        remoteStorage.mkdir(dirs);
        for (Index.Entry changedEntry : changedEntries) {
            objectIdsToUpload.add(changedEntry.getObjectId());
        }

        // 2. 上传commitObject，上传treeObject
        for (List<CommitChainItemSingleParent> singleChain : singleParentChainPaths) {
            for (CommitChainItemSingleParent commitChainItem : singleChain) {
                // 没有用uploadCommitObjectAndTreeObjectRecursive是为了减少不必要的上传
                // 查找变化的treeObjectId
                Map<String, String> parentPath2TreeObjectIdMap = new HashMap<>();
                if (commitChainItem.getParent() != null && !Objects.equals(commitChainItem.getParent().getCommitObjectId(), EMPTY_OBJECT_ID)) {
                    getChangedTreeObjectRecursive(commitChainItem.getParent().getCommitObjectId(), "", parentPath2TreeObjectIdMap);
                }
                Map<String, String> thisPath2TreeObjectIdMap = new HashMap<>();
                getChangedTreeObjectRecursive(commitChainItem.getCommitObjectId(), "", thisPath2TreeObjectIdMap);
                Set<String> changedTreeObjectIds = new HashSet<>();
                for (String path : thisPath2TreeObjectIdMap.keySet()) {
                    if (parentPath2TreeObjectIdMap.get(path) != null && Objects.equals(parentPath2TreeObjectIdMap.get(path), thisPath2TreeObjectIdMap.get(path))) {
                        continue;
                    }
                    changedTreeObjectIds.add(thisPath2TreeObjectIdMap.get(path));
                }
                objectIdsToUpload.addAll(changedTreeObjectIds);

                // 上传commitObjectId
                objectIdsToUpload.add(commitChainItem.getCommitObjectId());
            }
        }

        // upload with session, dont resort
        remoteStorage.uploadBatch(objectIdsToUpload.stream().map(x -> TransportMapping.of(ObjectUtils.getObjectPath(config.getObjectsDir(), x), PathUtils.concat("objects", ObjectUtils.path(x)))).collect(Collectors.toList()));

        // 3. 写remote日志(异常回退)
        LogItem localCommitLogItem = localLogManager.getLogs().stream().filter(x -> Objects.equals(x.getCommitObjectId(), localCommitObjectId)).findFirst().orElse(null);
        if (localCommitLogItem == null) {
            throw new RuntimeException("log file error, maybe missing some commit");
        }
        List<LogItem> remoteLogs = remoteLogManager.getLogs();
        LogItem remoteLogItem = new LogItem();
        if (remoteLogs == null) {
            remoteLogItem.setParentCommitObjectId(EMPTY_OBJECT_ID);
        } else {
            remoteLogItem.setParentCommitObjectId(remoteCommitObjectId);
        }
        remoteLogItem.setCommitObjectId(localCommitLogItem.getCommitObjectId());
        remoteLogItem.setCommitterName(localCommitLogItem.getCommitterName());
        remoteLogItem.setCommitterEmail(localCommitLogItem.getCommitterEmail());
        remoteLogItem.setMessage("push");
        remoteLogItem.setMtime(System.currentTimeMillis());

        String currRemoteRefsDir = PathUtils.concat(config.getRefsRemotesDir(), remoteName);
        File remoteHeadFile = new File(currRemoteRefsDir, "master");
        File remoteHeadLockFile = new File(remoteHeadFile.getAbsolutePath() + ".lock");

        try {
            remoteLogManager.lock();
            remoteLogManager.appendToLock(remoteLogItem);

            // 5. 修改本地remote的head(异常回退)
            FileUtils.copyFile(new File(PathUtils.concat(config.getRefsHeadsDir(), "master")), remoteHeadLockFile);
            FileUtils.writeStringToFile(remoteHeadLockFile, localCommitObjectId, StandardCharsets.UTF_8);

            // 6. 上传remote的head
            //  upload remote head lock to remote head
            remoteStorage.upload(new File(PathUtils.concat(config.getRefsHeadsDir(), "master")),
                    PathUtils.concat("refs", "remotes", remoteName, "master"));

            Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            remoteLogManager.commit();
        } catch (Exception e) {
            log.error("上传head失败", e);
            FileUtils.deleteQuietly(remoteHeadLockFile);
            remoteLogManager.rollback();
            throw e;
        }
    }


    private void getChangedTreeObjectRecursive(String objectId, String path, Map<String, String> path2TreeObjectIdMap) throws IOException {
        ObjectEntity objectEntity = objectManager.read(objectId);
        switch (objectEntity.getType()) {
            case commit:
                CommitObjectData commitObjectData = CommitObjectData.parseFrom(objectEntity.getData());
                String tree = commitObjectData.getTree();
                path2TreeObjectIdMap.put("", tree);
                getChangedTreeObjectRecursive(tree, "", path2TreeObjectIdMap);
                break;
            case tree:
                TreeObjectData treeObjectData = TreeObjectData.parseFrom(objectEntity.getData());
                List<TreeObjectData.TreeEntry> entries = treeObjectData.getEntries();
                for (TreeObjectData.TreeEntry entry : entries) {
                    if (entry.getType() == ObjectEntity.Type.tree) {
                        String treePath = PathUtils.concat(path, entry.getName());
                        path2TreeObjectIdMap.put(treePath, entry.getObjectId());
                        getChangedTreeObjectRecursive(entry.getObjectId(), treePath, path2TreeObjectIdMap);
                    }
                }
                break;
            case blob:
                // do nothing
                break;
            default:
                throw new RuntimeException("type error");
        }
    }


    private void initRemoteDirs(String remoteName) throws IOException {
        Storage remoteStorage = remoteStorageMap.get(remoteName);
        if (remoteStorage == null) {
            throw new RuntimeException("remoteStorage is not exist");
        }
        if (!remoteStorage.exists(PathUtils.concat("refs", "remotes", remoteName))) {
            remoteStorage.mkdir("");
            remoteStorage.mkdir("objects");
            remoteStorage.mkdir(PathUtils.concat("objects","pack"));
            remoteStorage.mkdir(PathUtils.concat("objects","info"));
            remoteStorage.mkdir("refs");
            remoteStorage.mkdir(PathUtils.concat("refs", "remotes"));
            remoteStorage.mkdir(PathUtils.concat("refs", "remotes", remoteName));
        }
    }

    public void repack() throws IOException {
        repack(100000);
    }

    /**
     * 打包原则： 根据commitChain分段打包: 最新的提交在最新的pack, 旧的pack随着commit的变多会逐渐稳定，内容也会不变。 只要limit不变，多次打包内容不变
     */
    public void repack(int limit) throws IOException {
        String localCommitObjectId = findLocalCommitObjectId();

        Map<String, Block> objectId2BlockMap = new HashMap<>();
        CommitChainItem commitChainHead = getCommitChainHead(localCommitObjectId, EMPTY_OBJECT_ID, objectManager);
        List<List<CommitChainItemSingleParent>> commitPaths = pathsToSingleParentCommitChains(getChainPaths(commitChainHead));
        for (List<CommitChainItemSingleParent> commitPath : commitPaths) {
            List<List<Index.Entry>> commits = new ArrayList<>();
            for (CommitChainItemSingleParent commitChainItemSingleParent : commitPath) {
                List<Index.Entry> treeAndBlobs = Index.generateTreeAndBlobFromCommit(commitChainItemSingleParent.getCommitObjectId(), objectManager);
                if (treeAndBlobs != null) {
                    commits.add(treeAndBlobs);
                }
            }
            //         commit   tree1   path1      path2      path3
            // index1                   objectId1  objectId2
            // index2                   objectId3             objectId4
            List<Index.Entry> allEntries = commits.stream().flatMap(Collection::stream).collect(Collectors.toList());
            Map<String, List<Index.Entry>> pathHistory = new LinkedHashMap<>();
            for (Index.Entry entry : allEntries) {
                pathHistory.putIfAbsent(entry.getPath(), new ArrayList<>());
                List<Index.Entry> entries = pathHistory.get(entry.getPath());
                entries.add(entry);
            }
            // commit
            log.debug("firstCommit:{}", commitPath.get(0).getCommitObjectId());
            List<Index.Entry> commitEntry = commitPath.stream().map(x -> {
                Index.Entry entry = new Index.Entry();
                entry.setObjectId(x.getCommitObjectId());
                entry.setType(ObjectEntity.Type.commit);
                entry.setPath("commit");
                return entry;
            }).collect(Collectors.toList());
            pathHistory.put("commit", commitEntry);

            // blob and tree
            for (List<Index.Entry> entries : pathHistory.values()) {
                Index.Entry lastEntry = null;
                int i = 0;
                for (Index.Entry entry : entries) {
                    if (i == 0) {
                        ObjectEntity targetObjectEntity = objectManager.read(entry.getObjectId());
                        BaseBlock baseBlock = new BaseBlock(entry.getObjectId(), entry.getType(), targetObjectEntity.getData());
                        objectId2BlockMap.putIfAbsent(entry.getObjectId(), baseBlock);

                        // region debug
                        log.debug(entry.getType() + ":" + entry.getObjectId());
                        if (entry.getType() == ObjectEntity.Type.commit) {
                            CommitObjectData commitObjectData = CommitObjectData.parseFrom(targetObjectEntity.getData());
                            log.debug("commitData:" + commitObjectData);
                        }
                        // endregion
                    } else {
                        ObjectEntity targetObjectEntity = objectManager.read(entry.getObjectId());
                        if (targetObjectEntity.isEmpty()) {
                            continue;
                        }
                        byte[] target = targetObjectEntity.getData();

                        ObjectEntity baseObjectEntity = objectManager.read(lastEntry.getObjectId());
                        byte[] base = baseObjectEntity.getData();

                        DeltaBlock deltaBlock = new RefDeltaBlock(entry.getObjectId(), DeltaUtils.makeDeltas(target, base), lastEntry.getObjectId());
                        objectId2BlockMap.putIfAbsent(entry.getObjectId(), deltaBlock);

                        // region debug
                        log.debug(entry.getType() + ":" + entry.getObjectId());
                        if (entry.getType() == ObjectEntity.Type.commit) {
                            CommitObjectData commitObjectData = CommitObjectData.parseFrom(targetObjectEntity.getData());
                            log.debug("commitData:" + commitObjectData);
                        }
                        // endregion
                    }
                    lastEntry = entry;
                    i++;
                }
            }
        }

        LinkedHashSet<Block> blocks = new LinkedHashSet<>();
        sortBlocksByCommitChain(Collections.singletonList(commitChainHead), objectId2BlockMap, blocks);

        // region debug
        for (Block block : blocks) {
            log.debug(block.getClass().getSimpleName() + ":" + block.getObjectId());
        }
        // endregion

        PackFile finalPackFile = new PackFile();
        finalPackFile.setBlockList(new ArrayList<>(blocks));
        finalPackFile.setHeader(new PackFile.Header(1, blocks.size()));

        List<PackFile> subPackFiles = finalPackFile.split(limit);
        int size = PackFileFormatter.size(finalPackFile);
        log.debug("size:" + size);

        PackInfo packInfo = new PackInfo();
        List<PackReader.PackPair> packPairs = new ArrayList<>();
        for (PackFile subPackFile : subPackFiles) {
            File packDir = new File(config.getObjectPackDir());
            FileUtils.forceMkdir(packDir);
            File packDataTmpFile = File.createTempFile("pack_", ".pack_tmp", packDir);
            File packIndexTmpFile = File.createTempFile("pack_", ".idx_tmp", packDir);
            PackWriter.write(subPackFile, packDataTmpFile, packIndexTmpFile);
            String checksum = ObjectUtils.bytesToHex(subPackFile.getTrailer().getChecksum());
            File packDataFile = new File(packDir, "pack_" + checksum + ".pack");
            File packIndexFile = new File(packDir, "pack_" + checksum + ".idx");
            Files.move(packDataTmpFile.toPath(), packDataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.move(packIndexTmpFile.toPath(), packIndexFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);


            // write pack info
            packInfo.add(packDataFile.getName());

            // region debug
            List<Block> blockList = subPackFile.getBlockList();
            for (Block block : blockList) {
                log.debug(block.getClass().getSimpleName() + ":" + block.getObjectId());
            }
            log.debug("\n");
            // endregion

            packPairs.add(new PackReader.PackPair(packIndexFile, packDataFile));
        }

        // write pack info tmp
        String objectInfoDir = config.getObjectInfoDir();
        File objectInfoDirFile = new File(objectInfoDir);
        FileUtils.forceMkdir(objectInfoDirFile);
        File packsTmpFile = File.createTempFile("packs_", ".tmp", objectInfoDirFile);
        FileUtils.writeStringToFile(packsTmpFile, JsonUtils.writeValueAsString(packInfo), StandardCharsets.UTF_8);

        // delete old packs
        Set<String> newPackNames = packInfo.getItems().stream().map(PackInfo.Item::getName).collect(Collectors.toSet());
        File oldPackInfoFile = new File(PathUtils.concat(objectInfoDir, "packs"));
        if (oldPackInfoFile.exists()) {
            PackInfo oldPackInfo = JsonUtils.readValue(FileUtils.readFileToString(oldPackInfoFile, StandardCharsets.UTF_8), PackInfo.class);
            if (oldPackInfo != null) {
                for (PackInfo.Item item : oldPackInfo.getItems()) {
                    String packPath = PathUtils.concat(config.getObjectPackDir(), item.getName());
                    if (newPackNames.contains(item.getName())) {
                        // 新的包和旧包重名, 表明这个未变化, 不删除
                        continue;
                    }
                    FileUtils.deleteQuietly(new File(packPath));
                    FileUtils.deleteQuietly(new File(PackUtils.getIndexPath(packPath)));
                }
            }
        }

        // delete packed objectIds
        List<String> objectIds = PackReader.readAllObjectIds(packPairs);
        for (String objectId : objectIds) {
            log.debug("pending deleting loose object:" + objectId);
            // no delete for now
//            objectManager.deleteLooseObject(objectId);
        }

        // write pack info
        Files.move(packsTmpFile.toPath(), oldPackInfoFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // region debug
        for (PackFile subPackFile : subPackFiles) {
            log.debug("pack file checksum:" + ObjectUtils.bytesToHex(subPackFile.getTrailer().getChecksum()));
        }
        // endregion

        List<ObjectEntity> objectEntities = PackReader.readAllObjects(packPairs);
        for (ObjectEntity objectEntity : objectEntities) {
            if (objectEntity.getType() == ObjectEntity.Type.blob) {
                BlobObjectData blobObjectData = BlobObjectData.parseFrom(objectEntity.getData());
                log.debug("blob: " + new String(blobObjectData.getData()));
            }

            if (objectEntity.getType() == ObjectEntity.Type.tree) {
                TreeObjectData treeObjectData = TreeObjectData.parseFrom(objectEntity.getData());
                log.debug("tree: " + treeObjectData.getEntries());
            }

            if (objectEntity.getType() == ObjectEntity.Type.commit) {
                CommitObjectData commitObjectData = CommitObjectData.parseFrom(objectEntity.getData());
                log.debug("commit: " + commitObjectData);
            }
        }


        // todo: optimization: index -> fileHistoryChain (rename)


    }

    /**
     * 除upload部分，其他与push方法相同
     */
    public void packAndPush(String remoteName) throws IOException {

        Storage remoteStorage = remoteStorageMap.get(remoteName);
        if (remoteStorage == null) {
            throw new RuntimeException("remoteStorage is not exist");
        }

        LogManager remoteLogManager = remoteLogManagerMap.get(remoteName);
        if (remoteLogManager == null) {
            throw new RuntimeException("remoteLogManager is not exist");
        }

        initRemoteDirs(remoteName);

        // fetch哪些就push哪些
        // 1. 根据commit链, 将所有提交导致的变化objectId全部上传
        String localCommitObjectId = findLocalCommitObjectId();
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);

        if (Objects.equals(localCommitObjectId, remoteCommitObjectId)) {
            log.info("nothing changed, no push");
            return;
        }

        log.info("repack start ... ");
        repack();
        log.info("repack end ... ");


        // upload packs
        log.info("upload packs start ... ");
        File packsInfoFile = new File(PathUtils.concat(config.getObjectInfoDir(), "packs"));
        if (!packsInfoFile.exists()) {
            push(remoteName);
            return;
        }
        PackInfo packInfo = JsonUtils.readValue(packsInfoFile, PackInfo.class);
        if (packInfo == null) {
            push(remoteName);
            return;
        }
        List<PackInfo.Item> items = packInfo.getItems();
        for (PackInfo.Item item : items) {

            String remotePackPath = PathUtils.concat("objects", "pack", item.getName());
            if (remoteStorage.exists(remotePackPath)) {
                log.info(item.getName() + " pack exists, no upload");
                continue;
            }
            String packPath = PathUtils.concat(config.getObjectPackDir(), item.getName());
            remoteStorage.upload(new File(packPath), remotePackPath);

            String remoteIndexPath = PackUtils.getIndexPath(remotePackPath);
            if (remoteStorage.exists(remoteIndexPath)) {
                log.info(item.getName() + " idx exists, no upload");
                continue;
            }
            String indexPath = PackUtils.getIndexPath(packPath);
            remoteStorage.upload(new File(indexPath), remoteIndexPath);

        }
        log.info("upload packs end ... ");


        Set<String> newPackFileNames = items.stream().map(PackInfo.Item::getName).collect(Collectors.toSet());

        // pack info 移动到packs.old
        log.info("move remote pack to packs.old start ... ");
        String remotePackInfoPath = PathUtils.concat("objects", "info", "packs");
        String oldRemotePackInfoPath = PathUtils.concat("objects", "info", "packs.old");
        remoteStorage.move(remotePackInfoPath, oldRemotePackInfoPath, true);
        log.info("move remote pack to packs.old end ... ");

        // 写入新pack info
        log.info("upload pack info start ... ");
        try {
            remoteStorage.upload(packsInfoFile, remotePackInfoPath);
        } catch (Exception e) {
            remoteStorage.move(oldRemotePackInfoPath, remotePackInfoPath, true);
            log.error("upload new pack info error, rollback",e);
        }
        log.info("upload pack info end ... ");

        log.info("delete old pack start ... ");
        if (remoteStorage.exists(oldRemotePackInfoPath)) {
            // 删除旧 pack files
            String oldPackInfoStr = remoteStorage.readFullToString(oldRemotePackInfoPath);
            PackInfo oldPackInfo = JsonUtils.readValue(oldPackInfoStr, PackInfo.class);
            if (oldPackInfo != null) {
                for (PackInfo.Item item : oldPackInfo.getItems()) {
                    if (newPackFileNames.contains(item.getName())) {
                        continue;
                    }
                    remoteStorage.delete(PathUtils.concat("objects", "pack", item.getName()));
                    remoteStorage.delete(PackUtils.getIndexPath(PathUtils.concat("objects", "pack", item.getName())));
                }
            }
            log.info("delete old pack end ... ");


            // 删除旧 pack info file
            log.info("delete old pack info start ... ");
            remoteStorage.delete(oldRemotePackInfoPath);
            log.info("delete old pack info end ... ");
        }




        // 3. 写remote日志(异常回退)
        log.info("write remote log start ... ");
        LogItem localCommitLogItem = localLogManager.getLogs().stream().filter(x -> Objects.equals(x.getCommitObjectId(), localCommitObjectId)).findFirst().orElse(null);
        if (localCommitLogItem == null) {
            throw new RuntimeException("log file error, maybe missing some commit");
        }
        List<LogItem> remoteLogs = remoteLogManager.getLogs();
        LogItem remoteLogItem = new LogItem();
        if (remoteLogs == null) {
            remoteLogItem.setParentCommitObjectId(EMPTY_OBJECT_ID);
        } else {
            remoteLogItem.setParentCommitObjectId(remoteCommitObjectId);
        }
        remoteLogItem.setCommitObjectId(localCommitLogItem.getCommitObjectId());
        remoteLogItem.setCommitterName(localCommitLogItem.getCommitterName());
        remoteLogItem.setCommitterEmail(localCommitLogItem.getCommitterEmail());
        remoteLogItem.setMessage("push");
        remoteLogItem.setMtime(System.currentTimeMillis());

        String currRemoteRefsDir = PathUtils.concat(config.getRefsRemotesDir(), remoteName);
        File remoteHeadFile = new File(currRemoteRefsDir, "master");
        File remoteHeadLockFile = new File(remoteHeadFile.getAbsolutePath() + ".lock");

        try {
            remoteLogManager.lock();
            remoteLogManager.appendToLock(remoteLogItem);

            // 5. 修改本地remote的head(异常回退)
            FileUtils.copyFile(new File(PathUtils.concat(config.getRefsHeadsDir(), "master")), remoteHeadLockFile);
            FileUtils.writeStringToFile(remoteHeadLockFile, localCommitObjectId, StandardCharsets.UTF_8);

            // 6. 上传remote的head
            //  upload remote head lock to remote head
            remoteStorage.upload(new File(PathUtils.concat(config.getRefsHeadsDir(), "master")),
                    PathUtils.concat("refs", "remotes", remoteName, "master"));

            Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            remoteLogManager.commit();
        } catch (Exception e) {
            log.error("上传head失败", e);
            FileUtils.deleteQuietly(remoteHeadLockFile);
            remoteLogManager.rollback();
            throw e;
        }
        log.info("write remote log end ... ");
    }

    /**
     * 按commitChain的顺序排blocks, 多个parent的用广度优先进行遍历
     */
    private void sortBlocksByCommitChain(List<CommitChainItem> commits, Map<String, Block> objectId2BlockMap, LinkedHashSet<Block> blocks) throws IOException {

        if (CollectionUtils.isEmpty(commits)) {
            return;
        }

        for (CommitChainItem commit : commits) {
            String commitObjectId = commit.getCommitObjectId();
            if (StringUtils.equals(commitObjectId, EMPTY_OBJECT_ID)) {
                continue;
            }
            Block commitBlock = objectId2BlockMap.get(commitObjectId);
            if (commitBlock == null) {
                throw new RuntimeException("block is not exists");
            }
            blocks.add(commitBlock);
            List<Index.Entry> entries = Index.generateTreeAndBlobFromCommit(commitObjectId, objectManager);
            if (entries == null) {
                continue;
            }
            for (Index.Entry entry : entries) {
                Block treeOrBlobBlock = objectId2BlockMap.get(entry.getObjectId());
                if (treeOrBlobBlock == null) {
                    throw new RuntimeException("block is not exists");
                }
                blocks.add(treeOrBlobBlock);
            }
        }

        List<CommitChainItem> parents = commits.stream().flatMap(x -> x.getParents().stream()).collect(Collectors.toList());
        sortBlocksByCommitChain(parents, objectId2BlockMap, blocks);
    }

    public GitLiteConfig getConfig() {
        return config;
    }

    @Data
    private static class FileNode {
        private File file;
        private List<FileNode> children = new ArrayList<>();
        private String objectId;
        private ObjectEntity.Type type;

        public FileNode(File file) {
            this.file = file;
            if (file.isDirectory()) {
                type = ObjectEntity.Type.tree;
            }
            if (file.isFile()) {
                type = ObjectEntity.Type.blob;
            }
        }

        void addChild(FileNode fileNode) {
            children.add(fileNode);
        }

        String getFileName() {
            return file.getName();
        }
    }


}
