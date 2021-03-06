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
import com.beyond.jgit.object.ObjectManagerFactory;
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
import java.util.stream.Stream;

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
        this.objectManager = ObjectManagerFactory.get(config.getObjectsDir());
        this.indexManager = new IndexManager(config.getIndexPath());
        this.localLogManager = new LogManager(PathUtils.concat(config.getLogsHeadsDir(), "master.json"));

        this.remoteLogManagerMap = new HashMap<>();
        this.remoteStorageMap = new HashMap<>();

        for (GitLiteConfig.RemoteConfig remoteConfig : config.getRemoteConfigs()) {
            remoteLogManagerMap.put(remoteConfig.getRemoteName(), new LogManager(PathUtils.concat(config.getLogsRemotesDir(), remoteConfig.getRemoteName(), "master.json")));
            if (remoteConfig.getRemoteUrl().startsWith("http://") || remoteConfig.getRemoteUrl().startsWith("https://")) {
                if (StringUtils.isNotBlank(remoteConfig.getRemoteTmpDir())) {
                    remoteStorageMap.put(remoteConfig.getRemoteName(),
                            new SardineStorage(PathUtils.concat(remoteConfig.getRemoteUrl()),
                                    remoteConfig.getRemoteUserName(),
                                    remoteConfig.getRemotePassword(),
                                    PathUtils.concat(remoteConfig.getRemoteTmpDir(), remoteConfig.getRemoteName(), "master.ed"),
                                    PathUtils.concat(remoteConfig.getRemoteTmpDir(), remoteConfig.getRemoteName(), "session")));
                } else {
                    remoteStorageMap.put(remoteConfig.getRemoteName(),
                            new SardineStorage(remoteConfig.getRemoteUrl(),
                                    remoteConfig.getRemoteUserName(),
                                    remoteConfig.getRemotePassword()));
                }
            } else {
                remoteStorageMap.put(remoteConfig.getRemoteName(), new FileStorage(remoteConfig.getRemoteUrl()));
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

        // ?????? gitignore
        if (StringUtils.isNotBlank(config.getIgnorePath()) && new File(config.getIgnorePath()).exists()) {
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

    public String commit(String message, String... parents) throws IOException {
        return commit(IndexManager.parseIndex(config.getIndexPath()), message, parents);
    }

    public String commit(Index index, String message, String... parents) throws IOException {

        Index committedIndex = Index.generateFromCommit(findLocalCommitObjectId(), objectManager);
        IndexDiffResult committedDiff = IndexDiffer.diff(index, committedIndex);
        if (!committedDiff.isChanged()) {
            log.debug("nothing changed, no commit");
            return "nothing changed";
        }

        ObjectEntity tree = addTreeFromIndex(index);
        ObjectEntity commit = addCommitObject(tree, message, parents);
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
        return addBlobObject(FileUtils.readFileToByteArray(file));
    }

    private ObjectEntity addBlobObject(byte[] blobData) throws IOException {
        BlobObjectData blobObjectData = new BlobObjectData();
        blobObjectData.setData(blobData);

        ObjectEntity objectEntity = new ObjectEntity();
        objectEntity.setType(ObjectEntity.Type.blob);
        objectEntity.setData(blobObjectData.toBytes());
        objectManager.write(objectEntity);
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
        if (ArrayUtils.isEmpty(parents)) {
            String localCommitObjectId = findLocalCommitObjectId();
            if (localCommitObjectId == null) {
                commitObjectData.addParent(EMPTY_OBJECT_ID);
            } else {
                commitObjectData.addParent(localCommitObjectId);
            }
        } else {
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
        if (!remoteStorage.exists(PathUtils.concat(".git","refs", "remotes", remoteName, "master"))) {
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
        String remoteHeadPath = PathUtils.concat(config.getRefsRemotesDir(), remoteName, "master");
        remoteStorage.download(PathUtils.concat(".git","refs", "remotes", remoteName, "master"), remoteHeadPath);

        // clone packs
        fetchPacks(remoteName);

        // ?????? remote head ????????????????????????objects
        String webRemoteLatestCommitObjectId = findRemoteCommitObjectId(remoteName);
        CommitChainItem chainHead = getRemoteCommitChainHead(webRemoteLatestCommitObjectId, null, remoteStorage);
        chainHead.dfsWalk(commitChainItem -> {
            // olderCommitObjectId???parents???????????? ????????????????????????????????????olderCommitObjectId
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
        if (!remoteStorage.exists(PathUtils.concat(".git","refs", "remotes", remoteName, "master"))) {
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
        String remoteHeadLockPath = PathUtils.concat(config.getRefsRemotesDir(), remoteName, "master.lock");
        File remoteHeadLockFile = new File(remoteHeadLockPath);
        if (remoteHeadLockFile.exists()) {
            log.error("remote master is locked, path:{}", remoteHeadLockFile.getAbsolutePath());
            throw new RuntimeException("remote master is locked");
        }
        remoteStorage.download(PathUtils.concat(".git","refs", "remotes", remoteName, "master"), remoteHeadLockPath);

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

        // ??????packs
        fetchPacks(remoteName);

        // ?????? remote head ????????????????????????objects
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);
        String remoteLockCommitObjectId = findRemoteLockCommitObjectId(remoteName);
        if (!remoteHeadFile.exists() || logs == null) {
            log.warn("local/remote log is empty, no fetch");
            Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        }
        // ???remoteLog, remoteLog???????????????,?????????. ??????commitObject??????parent???????????????
        CommitChainItem chainHead = getRemoteCommitChainHead(remoteLockCommitObjectId, remoteCommitObjectId, remoteStorage);
        chainHead.dfsWalk(commitChainItem -> {
            // olderCommitObjectId???parents???????????? ????????????????????????????????????olderCommitObjectId
            if (CollectionUtils.isNotEmpty(commitChainItem.getParents())) {
                downloadByObjectIdRecursive(commitChainItem.getCommitObjectId(), remoteStorage);
            }
        });


        // update head
        Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

    }

    /**
     * ??????packs
     */
    private void fetchPacks(String remoteName) throws IOException {
        Storage remoteStorage = remoteStorageMap.get(remoteName);
        String remotePackInfoPath = PathUtils.concat(".git","objects", "info", "packs");
        String packInfoTmpPath = PathUtils.concat(config.getObjectInfoDir(), "packs.tmp");
        if (remoteStorage.exists(remotePackInfoPath)) {
            remoteStorage.download(remotePackInfoPath, packInfoTmpPath);
        }
        File packInfoFileTmp = new File(packInfoTmpPath);
        String packInfoStr = FileUtils.readFileToString(packInfoFileTmp, StandardCharsets.UTF_8);
        PackInfo packInfo = JsonUtils.readValue(packInfoStr, PackInfo.class);
        if (packInfo != null) {
            for (PackInfo.Item item : packInfo.getItems()) {
                String remotePackPath = PathUtils.concat(".git","objects", "pack", item.getName());
                String localPackPath = PathUtils.concat(config.getObjectPackDir(), item.getName());
                remoteStorage.download(remotePackPath,localPackPath);
                remoteStorage.download(PackUtils.getIndexPath(remotePackPath), PackUtils.getIndexPath(localPackPath));
            }
        }

        File packInfoFile = new File(config.getObjectInfoDir(), "packs");
        FileUtil.move(packInfoFileTmp, packInfoFile);
    }


    // ???????????????
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
            String objectPath = ObjectUtils.getObjectPath(config.getObjectsDir(), newerCommitObjectId);
            File objectFile = new File(objectPath);
            FileUtils.forceMkdirParent(objectFile);
            remoteStorage.download(PathUtils.concat(".git","objects", ObjectUtils.path(newerCommitObjectId)), objectPath);
        }
        ObjectEntity commitObjectEntity = objectManager.read(newerCommitObjectId);
        List<String> parents = CommitObjectData.parseFrom(commitObjectEntity.getData()).getParents();
        //  merge???????????????
        for (String parent : parents) {
            downloadCommitObjectsBetween(parent, olderCommitObjectId, remoteStorage);
        }
    }

    private void downloadByObjectIdRecursive(String objectId, Storage remoteStorage) throws IOException {
        if (!objectManager.exists(objectId)) {
            String objectPath = ObjectUtils.getObjectPath(config.getObjectsDir(), objectId);
            File objectFile = new File(objectPath);
            FileUtils.forceMkdirParent(objectFile);
            remoteStorage.download(PathUtils.concat(".git","objects", ObjectUtils.path(objectId)), objectPath);
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


    public void checkout(String commitObjectId, List<String> targetPaths) throws IOException {
        Index targetIndex = Index.generateFromCommit(commitObjectId, objectManager);
        for (Index.Entry entry : targetIndex.getEntries()) {
            String absPath = PathUtils.concat(config.getLocalDir(), entry.getPath());
            for (String targetPath : targetPaths) {
                if (PathUtils.equals(entry.getPath(), targetPath)) {
                    ObjectEntity objectEntity = objectManager.read(entry.getObjectId());
                    if (objectEntity.getType() == ObjectEntity.Type.blob) {
                        BlobObjectData blobObjectData = BlobObjectData.parseFrom(objectEntity.getData());
                        FileUtils.writeByteArrayToFile(new File(absPath), blobObjectData.getData());
                    }
                }
            }

        }
    }

    /**
     * ???remote?????????merge?????????commit???
     */
    public void merge(String remoteName) throws IOException {
        // ?????????
        // 1. ????????????????????????
        // 2. ????????????commit??????commit???????????????????????????(?????????????????????????????? ?????????????????? ???????????????)
        // 3. local???remote?????????????????????????????? ????????????
        // 4. ??????????????????????????????????????????key)
        // ??????log??????local???remote???????????????????????????commit


        String localCommitObjectId = findLocalCommitObjectId();
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);

        // region debug
        if (log.isDebugEnabled()) {
            if (StringUtils.isNotBlank(localCommitObjectId)) {
                CommitChainItem localCommitChainRoot = getCommitChainHead(localCommitObjectId, EMPTY_OBJECT_ID, objectManager);
                localCommitChainRoot.print();
            }
            if (StringUtils.isNotBlank(remoteCommitObjectId)) {
                CommitChainItem remoteCommitChainRoot = getCommitChainHead(remoteCommitObjectId, EMPTY_OBJECT_ID, objectManager);
                remoteCommitChainRoot.print();
            }
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
                    if (intersectionCommitObjectId != null) {
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
                    if (intersectionCommitObjectId != null) {
                        break;
                    }
                }
                remoteCommitChainItemLazys = newRemoteCommitChainItemLazys;

                if (intersectionCommitObjectId != null) {
                    break;
                }

                if (CollectionUtils.isEmpty(localCommitChainItemLazys) && CollectionUtils.isEmpty(remoteCommitChainItemLazys)) {
                    break;
                }
            }
        }

        if (intersectionCommitObjectId == null) {
            log.warn("no intersectionCommitObjectId, cover.");
        }

        Index intersectionIndex = Index.generateFromCommit(intersectionCommitObjectId, objectManager);

        Index committedHeadIndex = Index.generateFromCommit(localCommitObjectId, objectManager);
        IndexDiffResult committedDiff = IndexDiffer.diff(committedHeadIndex, intersectionIndex);
        log.debug("committedDiff: {}", JsonUtils.writeValueAsString(committedDiff));

        Index remoteHeadIndex = Index.generateFromCommit(remoteCommitObjectId, objectManager);
        IndexDiffResult remoteDiff = IndexDiffer.diff(remoteHeadIndex, intersectionIndex);
        log.debug("remoteDiff: {}", JsonUtils.writeValueAsString(remoteDiff));

        // - ???????????????????????????commit - ??????????????????, ??????index, ?????? index ??????commit

        Set<Index.Entry> committedChanged = new HashSet<>();
        committedChanged.addAll(committedDiff.getAdded());
        committedChanged.addAll(committedDiff.getUpdated());
        committedChanged.addAll(committedDiff.getRemoved());

        Set<Index.Entry> remoteChanged = new HashSet<>();
        remoteChanged.addAll(remoteDiff.getAdded());
        remoteChanged.addAll(remoteDiff.getUpdated());
        remoteChanged.addAll(remoteDiff.getRemoved());

        if (!committedDiff.isChanged() && remoteDiff.isChanged()) {
            // ?????????????????????????????????????????????, ?????????HEAD
            File headRefFile = getHeadRefFile();
            FileUtils.writeStringToFile(headRefFile, remoteCommitObjectId, StandardCharsets.UTF_8);
            return;
        }

        if (committedDiff.isChanged() && !remoteDiff.isChanged()) {
            // ?????????????????????????????????????????????, ?????????
            // do nothing, wait for push
            return;
        }

        Map<String, String> committedChangedPath2ObjectIdMap = committedChanged.stream().collect(Collectors.toMap(Index.Entry::getPath, Index.Entry::getObjectId));
        Map<String, String> remoteChangedPath2ObjectIdMap = remoteChanged.stream().collect(Collectors.toMap(Index.Entry::getPath, Index.Entry::getObjectId));

        Map<String, Index.Entry> remoteIndexPath2Entry = remoteHeadIndex == null ? Collections.emptyMap() : remoteHeadIndex.getEntries().stream().collect(Collectors.toMap(Index.Entry::getPath, x -> x));

        Collection<String> intersection = CollectionUtils.intersection(committedChangedPath2ObjectIdMap.keySet(), remoteChangedPath2ObjectIdMap.keySet());
        intersection.removeIf(x -> Objects.equals(committedChangedPath2ObjectIdMap.get(x), remoteChangedPath2ObjectIdMap.get(x)));

        // todo : test
        if (intersectionIndex != null && CollectionUtils.isNotEmpty(intersection)) {
            log.debug("conflicted paths:{}", JsonUtils.writeValueAsString(intersection));
            // merge conflicted
            List<String> remotePathsToCheckout = new ArrayList<>();
            Map<String, String> intersectionPath2ObjectIdMap = intersectionIndex.getEntries().stream().collect(Collectors.toMap(Index.Entry::getPath, Index.Entry::getObjectId));
            for (String pathInIndex : intersection) {
                String localObjectId = committedChangedPath2ObjectIdMap.get(pathInIndex);
                String remoteObjectId = remoteChangedPath2ObjectIdMap.get(pathInIndex);
                String intersectionObjectId = intersectionPath2ObjectIdMap.get(pathInIndex);

                ObjectEntity localCommit = objectManager.read(localCommitObjectId);
                ObjectEntity remoteCommit = objectManager.read(remoteCommitObjectId);

                boolean isLocalNewer = true;
                if (CommitObjectData.parseFrom(localCommit.getData()).getCommitTime() < CommitObjectData.parseFrom(remoteCommit.getData()).getCommitTime()) {
                    isLocalNewer = false;
                }

                // delete ??? update???????????????
                Set<String> localRemovedPath = committedDiff.getRemoved().stream().map(Index.Entry::getPath).collect(Collectors.toSet());
                Set<String> remoteRemovePath = remoteDiff.getRemoved().stream().map(Index.Entry::getPath).collect(Collectors.toSet());
                if (localRemovedPath.contains(pathInIndex)) {
                    if (isLocalNewer) {
                        committedHeadIndex.getEntries().removeIf(x -> Objects.equals(x.getPath(), pathInIndex));
                    } else {
                        if (remoteRemovePath.contains(pathInIndex)) {
                            committedHeadIndex.getEntries().removeIf(x -> Objects.equals(x.getPath(), pathInIndex));
                        } else {
                            committedHeadIndex.upsert(Collections.singletonList(remoteIndexPath2Entry.get(pathInIndex)));
                            remotePathsToCheckout.add(pathInIndex);
                        }
                    }
                    continue;
                }

                if (remoteRemovePath.contains(pathInIndex)) {
                    if (!isLocalNewer) {
                        committedHeadIndex.getEntries().removeIf(x -> Objects.equals(x.getPath(), pathInIndex));
                    }
                    continue;
                }

                // update ??? update ??????????????????patches ??????????????????
                String localStr = readBlobToString(localObjectId);
                String remoteStr = readBlobToString(remoteObjectId);
                String intersectionStr = readBlobToString(intersectionObjectId);

                DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
                LinkedList<DiffMatchPatch.Patch> localPatches = diffMatchPatch.patch_make(intersectionStr, localStr);
                LinkedList<DiffMatchPatch.Patch> remotePatches = diffMatchPatch.patch_make(intersectionStr, remoteStr);


                LinkedList<DiffMatchPatch.Patch> resultPatches = new LinkedList<>();
                for (DiffMatchPatch.Patch localPatch : localPatches) {
                    boolean isCross = false;
                    for (DiffMatchPatch.Patch remotePatch : remotePatches) {
                        if (GoogleDiffUtils.isCross(localPatch, remotePatch)) {
                            isCross = true;
                            break;
                        }
                    }
                    if (!isCross) {
                        resultPatches.add(localPatch);
                    } else {
                        if (isLocalNewer) {
                            resultPatches.add(localPatch);
                        }
                    }
                }
                for (DiffMatchPatch.Patch remotePatch : remotePatches) {
                    boolean isCross = false;
                    for (DiffMatchPatch.Patch localPatch : localPatches) {
                        if (GoogleDiffUtils.isCross(localPatch, remotePatch)) {
                            isCross = true;
                            break;
                        }
                    }
                    if (!isCross) {
                        resultPatches.add(remotePatch);
                    } else {
                        if (!isLocalNewer) {
                            resultPatches.add(remotePatch);
                        }
                    }
                }

                Object[] objects = diffMatchPatch.patch_apply(resultPatches, intersectionStr);
                String result = (String) objects[0];
                log.debug("merged result: {} : {}", pathInIndex, result);
                addBlobObject(result.getBytes());
                Index.Entry mergedEntry = new Index.Entry();
                mergedEntry.setType(ObjectEntity.Type.blob);
                mergedEntry.setPath(pathInIndex);
                mergedEntry.setObjectId(ObjectUtils.sha1hash(ObjectEntity.Type.blob, result.getBytes()));
                committedHeadIndex.upsert(Collections.singletonList(mergedEntry));
                remotePathsToCheckout.add(pathInIndex);
            }
            checkout(remoteCommitObjectId, remotePathsToCheckout);
        }

        // ????????????????????????path, ????????????????????????path
        remoteDiff.getAdded().removeIf(x -> intersection.contains(x.getPath()));
        remoteDiff.getUpdated().removeIf(x -> intersection.contains(x.getPath()));
        remoteDiff.getRemoved().removeIf(x -> intersection.contains(x.getPath()));
        if (committedHeadIndex == null) {
            committedHeadIndex = new Index();
        }
        committedHeadIndex.upsert(remoteDiff.getAdded());
        committedHeadIndex.upsert(remoteDiff.getUpdated());
        committedHeadIndex.remove(remoteDiff.getRemoved());

        log.debug(JsonUtils.writeValueAsString(committedHeadIndex));

        indexManager.save(committedHeadIndex);

        // checkout ??????????????????: ???checkout??????, ???checkout?????????????????????
        for (Index.Entry entry : remoteDiff.getRemoved()) {
            FileUtils.deleteQuietly(new File(PathUtils.concat(config.getLocalDir(), entry.getPath())));
        }
        List<String> remoteAddedOrUpdatedPaths = Stream.concat(remoteDiff.getAdded().stream(), remoteDiff.getUpdated().stream())
                .map(Index.Entry::getPath).collect(Collectors.toList());
        checkout(remoteCommitObjectId, remoteAddedOrUpdatedPaths);


        log.info("create merge commit");
        // ????????????local???remote???????????????merge
        commit("merge", localCommitObjectId, remoteCommitObjectId);
        log.info("merge committed");
    }

    private String readBlobToString(String objectId) throws IOException {
        ObjectEntity objectEntity = objectManager.read(objectId);
        BlobObjectData data = BlobObjectData.parseFrom(objectEntity.getData());
        return new String(data.getData());
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
        File headFile = new File(headPath);
        if (!headFile.exists()) {
            return null;
        }
        String ref = FileUtils.readFileToString(headFile, StandardCharsets.UTF_8);
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
     * ????????????packAndPush
     *
     * @param remoteName
     * @throws IOException
     */
    @Deprecated
    public void push(String remoteName) throws IOException {
        // todo: ?????????????????????????????????????

        Storage remoteStorage = remoteStorageMap.get(remoteName);
        if (remoteStorage == null) {
            throw new RuntimeException("remoteStorage is not exist");
        }
        LogManager remoteLogManager = remoteLogManagerMap.get(remoteName);
        if (remoteLogManager == null) {
            throw new RuntimeException("remoteLogManager is not exist");
        }

        initRemoteDirs(remoteName);

        // fetch?????????push??????
        // 1. ??????commit???, ??????????????????????????????objectId????????????
        String localCommitObjectId = findLocalCommitObjectId();
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);

        if (Objects.equals(localCommitObjectId, remoteCommitObjectId)) {
            log.info("nothing changed, no push");
            return;
        }

        // ???????????????remote????????????remote???????????????, ????????????????????????????????????????????????????????????
//        checkWebRemoteStatus(remoteName, remoteStorage, remoteLogManager);


        // fixme: clone ?????????????????????parent???object, clone????????????????????????objects? ????????????????????????????????????
        CommitChainItem chainHead = getCommitChainHead(localCommitObjectId, remoteCommitObjectId, objectManager);
        List<List<CommitChainItem>> chains = getChainPaths(chainHead);
        List<List<CommitChainItemSingleParent>> singleParentChainPaths = pathsToSingleParentCommitChains(chains);
        // ???????????????remoteCommitObjectId?????????
        singleParentChainPaths.removeIf(x -> {
            Set<String> chainCommitObjectIds = x.stream().map(CommitChainItemSingleParent::getCommitObjectId).collect(Collectors.toSet());
            return remoteCommitObjectId != null && !chainCommitObjectIds.contains(remoteCommitObjectId);
        });
        // ??????????????????????????????remoteCommitObjectId
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

                // 2. ??????objects
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
        Set<String> dirs = changedEntries.stream().map(x -> PathUtils.parent(ObjectUtils.path(x.getObjectId()))).map(x -> PathUtils.concat(".git","objects", x)).collect(Collectors.toSet());
        remoteStorage.mkdir(dirs);
        for (Index.Entry changedEntry : changedEntries) {
            objectIdsToUpload.add(changedEntry.getObjectId());
        }

        // 2. ??????commitObject?????????treeObject
        for (List<CommitChainItemSingleParent> singleChain : singleParentChainPaths) {
            for (CommitChainItemSingleParent commitChainItem : singleChain) {
                // ?????????uploadCommitObjectAndTreeObjectRecursive?????????????????????????????????
                // ???????????????treeObjectId
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

                // ??????commitObjectId
                objectIdsToUpload.add(commitChainItem.getCommitObjectId());
            }
        }

        // upload with session, dont resort
        remoteStorage.uploadBatch(objectIdsToUpload.stream().map(x -> TransportMapping.of(ObjectUtils.getObjectPath(config.getObjectsDir(), x), PathUtils.concat(".git","objects", ObjectUtils.path(x)))).collect(Collectors.toList()));

        // 3. ???remote??????(????????????)
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

            // 5. ????????????remote???head(????????????)
            FileUtils.copyFile(new File(PathUtils.concat(config.getRefsHeadsDir(), "master")), remoteHeadLockFile);
            FileUtils.writeStringToFile(remoteHeadLockFile, localCommitObjectId, StandardCharsets.UTF_8);

            // 6. ??????remote???head
            //  upload remote head lock to remote head
            remoteStorage.upload(PathUtils.concat(config.getRefsHeadsDir(), "master"),
                    PathUtils.concat(".git","refs", "remotes", remoteName, "master"));

            Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            remoteLogManager.commit();
        } catch (Exception e) {
            log.error("??????head??????", e);
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
        if (!remoteStorage.exists(PathUtils.concat(".git","refs", "remotes", remoteName))) {
            remoteStorage.mkdir("");
            remoteStorage.mkdir(PathUtils.concat(".git",""));
            remoteStorage.mkdir(PathUtils.concat(".git","objects"));
            remoteStorage.mkdir(PathUtils.concat(".git","objects", "pack"));
            remoteStorage.mkdir(PathUtils.concat(".git","objects", "info"));
            remoteStorage.mkdir(PathUtils.concat(".git","refs"));
            remoteStorage.mkdir(PathUtils.concat(".git","refs", "remotes"));
            remoteStorage.mkdir(PathUtils.concat(".git","refs", "remotes", remoteName));
        }
    }

    public void repack() throws IOException {
        repack(100000);
    }

    /**
     * ??????????????? ??????commitChain????????????: ???????????????????????????pack, ??????pack??????commit???????????????????????????????????????????????? ??????limit?????????????????????????????????
     */
    public void repack(int limit) throws IOException {
        String localCommitObjectId = findLocalCommitObjectId();

        Map<String, Block> objectId2BlockMap = new HashMap<>();
        CommitChainItem commitChainHead = getCommitChainHead(localCommitObjectId, EMPTY_OBJECT_ID, objectManager);
        List<List<CommitChainItemSingleParent>> commitPaths = pathsToSingleParentCommitChains(getChainPaths(commitChainHead));
        log.debug("commitPathsSize:{}, commitPathTotal:{}", commitPaths.size(), commitPaths.stream().mapToLong(Collection::size).sum());
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
                entry.setPath("__commit__");
                return entry;
            }).collect(Collectors.toList());
            pathHistory.put("__commit__", commitEntry);

            // blob and tree
            for (List<Index.Entry> entries : pathHistory.values()) {
                Index.Entry lastEntry = null;
                int i = 0;
                for (Index.Entry entry : entries) {
                    if (objectId2BlockMap.get(entry.getObjectId()) != null) {
                        continue;
                    }
                    if (i == 0) {
                        ObjectEntity targetObjectEntity = objectManager.read(entry.getObjectId());
                        BaseBlock baseBlock = new BaseBlock(entry.getObjectId(), entry.getType(), targetObjectEntity.getData());
                        objectId2BlockMap.putIfAbsent(entry.getObjectId(), baseBlock);

                        // region debug
                        if (log.isDebugEnabled()) {
                            log.debug(entry.getType() + ":" + entry.getObjectId());
                            if (entry.getType() == ObjectEntity.Type.commit) {
                                CommitObjectData commitObjectData = CommitObjectData.parseFrom(targetObjectEntity.getData());
                                log.debug("commitData:" + commitObjectData);
                            }
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
                        if (log.isDebugEnabled()) {
                            log.debug(entry.getType() + ":" + entry.getObjectId());
                            if (entry.getType() == ObjectEntity.Type.commit) {
                                CommitObjectData commitObjectData = CommitObjectData.parseFrom(targetObjectEntity.getData());
                                log.debug("commitData:" + commitObjectData);
                            }
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
        if (log.isDebugEnabled()) {
            for (Block block : blocks) {
                log.debug(block.getClass().getSimpleName() + ":" + block.getObjectId());
            }
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
            if (log.isDebugEnabled()) {
                List<Block> blockList = subPackFile.getBlockList();
                for (Block block : blockList) {
                    log.debug(block.getClass().getSimpleName() + ":" + block.getObjectId());
                }
                log.debug("\n");
            }
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
                        // ????????????????????????, ?????????????????????, ?????????
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
        if (log.isDebugEnabled()) {
            for (PackFile subPackFile : subPackFiles) {
                log.debug("pack file checksum:" + ObjectUtils.bytesToHex(subPackFile.getTrailer().getChecksum()));
            }

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
        }
        // endregion


        // todo: optimization: index -> fileHistoryChain (rename)


    }

    /**
     * ???upload??????????????????push????????????
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

        // fetch?????????push??????
        // 1. ??????commit???, ??????????????????????????????objectId????????????
        String localCommitObjectId = findLocalCommitObjectId();
        String remoteCommitObjectId = findRemoteCommitObjectId(remoteName);

        if (Objects.equals(localCommitObjectId, remoteCommitObjectId)) {
            log.info("nothing changed, no push");
            return;
        }

        fetch(remoteName);
        String remoteCommitObjectIdAfterFetch = findRemoteCommitObjectId(remoteName);
        if (!StringUtils.equals(remoteCommitObjectId, remoteCommitObjectIdAfterFetch)) {
            merge(remoteName);
            checkout();
            localCommitObjectId = findLocalCommitObjectId();
            remoteCommitObjectId = remoteCommitObjectIdAfterFetch;
        }

        log.info("repack start ... ");
        repack();
        log.info("repack end ... ");


        // upload packs
        log.info("upload packs start ... ");
        String packsInfoPath = PathUtils.concat(config.getObjectInfoDir(), "packs");
        File packsInfoFile = new File(packsInfoPath);
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

            String remotePackPath = PathUtils.concat(".git","objects", "pack", item.getName());
            if (remoteStorage.exists(remotePackPath)) {
                log.info(item.getName() + " pack exists, no upload");
                continue;
            }
            String packPath = PathUtils.concat(config.getObjectPackDir(), item.getName());
            remoteStorage.upload(packPath, remotePackPath);

            String remoteIndexPath = PackUtils.getIndexPath(remotePackPath);
            if (remoteStorage.exists(remoteIndexPath)) {
                log.info(item.getName() + " idx exists, no upload");
                continue;
            }
            String indexPath = PackUtils.getIndexPath(packPath);
            remoteStorage.upload(indexPath, remoteIndexPath);

        }
        log.info("upload packs end ... ");


        Set<String> newPackFileNames = items.stream().map(PackInfo.Item::getName).collect(Collectors.toSet());

        // pack info ?????????packs.old
        log.info("move remote pack to packs.old start ... ");
        String remotePackInfoPath = PathUtils.concat(".git", "objects", "info", "packs");
        String oldRemotePackInfoPath = PathUtils.concat(".git", "objects", "info", "packs.old");
        boolean oldPackInfoExists = remoteStorage.exists(oldRemotePackInfoPath);
        boolean moved = remoteStorage.move(remotePackInfoPath, oldRemotePackInfoPath, true);
        if (!moved && oldPackInfoExists) {
            throw new RuntimeException("move remote pack to packs.old error, some other push is running.");
        }
        log.info("move remote pack to packs.old end ... ");

        // ?????????pack info
        log.info("upload pack info start ... ");
        try {
            remoteStorage.upload(packsInfoPath, remotePackInfoPath);
        } catch (Exception e) {
            remoteStorage.move(oldRemotePackInfoPath, remotePackInfoPath, true);
            log.error("upload new pack info error, rollback", e);
        }
        log.info("upload pack info end ... ");

        log.info("delete old pack start ... ");
        if (remoteStorage.exists(oldRemotePackInfoPath)) {
            // ????????? pack files
            String oldPackInfoStr = remoteStorage.readFullToString(oldRemotePackInfoPath);
            PackInfo oldPackInfo = JsonUtils.readValue(oldPackInfoStr, PackInfo.class);
            if (oldPackInfo != null) {
                for (PackInfo.Item item : oldPackInfo.getItems()) {
                    if (newPackFileNames.contains(item.getName())) {
                        continue;
                    }
                    remoteStorage.delete(PathUtils.concat(".git","objects", "pack", item.getName()));
                    remoteStorage.delete(PackUtils.getIndexPath(PathUtils.concat(".git","objects", "pack", item.getName())));
                }
            }
            log.info("delete old pack end ... ");


            // ????????? pack info file
            log.info("delete old pack info start ... ");
            remoteStorage.delete(oldRemotePackInfoPath);
            log.info("delete old pack info end ... ");
        }


        // 3. ???remote??????(????????????)
        log.info("write remote log start ... ");
        String finalLocalCommitObjectId = localCommitObjectId;
        LogItem localCommitLogItem = localLogManager.getLogs().stream().filter(x -> Objects.equals(x.getCommitObjectId(), finalLocalCommitObjectId)).findFirst().orElse(null);
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

            // 5. ????????????remote???head(????????????)
            FileUtils.copyFile(new File(PathUtils.concat(config.getRefsHeadsDir(), "master")), remoteHeadLockFile);
            FileUtils.writeStringToFile(remoteHeadLockFile, localCommitObjectId, StandardCharsets.UTF_8);

            // 6. ??????remote???head
            //  upload local head to remote head
            boolean sourceExists = remoteStorage.copy(PathUtils.concat(".git", "refs", "remotes", remoteName, "master"), PathUtils.concat(".git", "refs", "remotes", remoteName, "master.lock"), false);
            if (sourceExists){
                String remoteHeadInWeb = remoteStorage.readFullToString(PathUtils.concat(".git","refs", "remotes", remoteName, "master"));
                if (!StringUtils.equals(remoteCommitObjectId, remoteHeadInWeb)) {
                    remoteStorage.delete(PathUtils.concat(".git","refs", "remotes", remoteName, "master.lock"));
                    throw new RuntimeException("remote head changed on pushing, please retry");
                }
                remoteStorage.upload(PathUtils.concat(config.getRefsHeadsDir(), "master"),
                        PathUtils.concat(".git","refs", "remotes", remoteName, "master"));
                remoteStorage.delete(PathUtils.concat(".git","refs", "remotes", remoteName, "master.lock"));
            }else{
                // ??????push, ???????
                remoteStorage.upload(PathUtils.concat(config.getRefsHeadsDir(), "master"),
                        PathUtils.concat(".git","refs", "remotes", remoteName, "master"));
            }

            Files.move(remoteHeadLockFile.toPath(), remoteHeadFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            remoteLogManager.commit();
        } catch (Exception e) {
            log.error("??????head??????", e);
            FileUtils.deleteQuietly(remoteHeadLockFile);
            remoteLogManager.rollback();
            throw e;
        }
        log.info("write remote log end ... ");
    }

    private boolean needFetchAndMerge(CommitChainItem commitChainHead, String targetCommitObjectId) {
        if (StringUtils.equals(commitChainHead.getCommitObjectId(), targetCommitObjectId)) {
            return false;
        }
        for (CommitChainItem parent : commitChainHead.getParents()) {
            if (!needFetchAndMerge(parent, targetCommitObjectId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * ???commitChain????????????blocks, ??????parent??????????????????????????????
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
