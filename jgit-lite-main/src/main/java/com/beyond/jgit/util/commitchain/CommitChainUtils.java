package com.beyond.jgit.util.commitchain;

import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.object.ObjectManager;
import com.beyond.jgit.object.data.CommitObjectData;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.*;

import static com.beyond.jgit.GitLite.EMPTY_OBJECT_ID;

/**
 * @author chenshipeng
 * @date 2021/06/18
 */
public class CommitChainUtils {

    /**
     * 包左包右
     */
    public static CommitChainItem getCommitChainHead(String newerCommitObjectId, String olderCommitObjectId, ObjectManager objectManager) throws IOException {
        if (Objects.equals(newerCommitObjectId, olderCommitObjectId)) {
            CommitChainItem commitChainItem = new CommitChainItem();
            commitChainItem.setCommitObjectId(olderCommitObjectId);
            return commitChainItem;
        }
        if (Objects.equals(newerCommitObjectId, EMPTY_OBJECT_ID)) {
            CommitChainItem commitChainItem = new CommitChainItem();
            commitChainItem.setCommitObjectId(EMPTY_OBJECT_ID);
            return commitChainItem;
        }
        ObjectEntity commitObjectEntity = objectManager.read(newerCommitObjectId);
        CommitChainItem commitChainItem = new CommitChainItem();
        commitChainItem.setCommitObjectId(newerCommitObjectId);
        List<String> parents = CommitObjectData.parseFrom(commitObjectEntity.getData()).getParents();
        // merge 时会有多个parent
        for (String parent : parents) {
            CommitChainItem parentItem = getCommitChainHead(parent, olderCommitObjectId, objectManager);
            commitChainItem.getParents().add(parentItem);
        }
        return commitChainItem;
    }


    /**
     * 根据chainRoot获取路径
     */
    public static List<List<CommitChainItem>> getChainPaths(CommitChainItem root) {
        List<List<CommitChainItem>> chains = new ArrayList<>();
        chains.add(new ArrayList<>());
        buildChainPaths(root, chains);
        return chains;
    }

    private static void buildChainPaths(CommitChainItem root, List<List<CommitChainItem>> chains) {
        if (root == null) {
            // 不会出现
            return;
        }
        List<CommitChainItem> parents = root.getParents();
        for (List<CommitChainItem> chain : chains) {
            chain.add(root);
        }
        List<List<CommitChainItem>> newChains = new ArrayList<>();
        for (CommitChainItem parent : parents) {
            for (List<CommitChainItem> chain : chains) {
                List<CommitChainItem> newChain = new ArrayList<>(chain);
                newChains.add(newChain);
            }
            buildChainPaths(parent, newChains);
        }
        if (CollectionUtils.isEmpty(parents)) {
            return;
        }
        chains.clear();
        chains.addAll(newChains);
    }

    /**
     * 包左不包右
     * 路径转为
     */
    public static List<List<CommitChainItemSingleParent>> pathsToSingleParentCommitChains(List<List<CommitChainItem>> chains) {
        List<List<CommitChainItemSingleParent>> singleCommitChains = new ArrayList<>();

        List<List<CommitChainItem>> reversedChains = new ArrayList<>();
        for (List<CommitChainItem> chain : chains) {
            List<CommitChainItem> reversedChain = new ArrayList<>(chain);
            Collections.reverse(reversedChain);
            reversedChains.add(reversedChain);
        }

        for (List<CommitChainItem> reversedChain : reversedChains) {
            List<CommitChainItemSingleParent> singleChain = new LinkedList<>();
            CommitChainItemSingleParent parent = null;
            for (CommitChainItem commitChainItem : reversedChain) {
                CommitChainItemSingleParent single = new CommitChainItemSingleParent();
                single.setCommitObjectId(commitChainItem.getCommitObjectId());
                single.setParent(parent);
                singleChain.add(0, single);
                parent = single;
            }
            singleCommitChains.add(singleChain);
        }
        return singleCommitChains;
    }

}
