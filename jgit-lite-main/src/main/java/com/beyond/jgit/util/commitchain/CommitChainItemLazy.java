package com.beyond.jgit.util.commitchain;

import com.beyond.jgit.GitLite;
import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.object.ObjectManager;
import com.beyond.jgit.object.data.CommitObjectData;
import lombok.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class CommitChainItemLazy extends CommitChainItem{

    private final ObjectManager objectManager;

    public CommitChainItemLazy(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public CommitChainItemLazy(String commitObjectId,ObjectManager objectManager) {
        this.objectManager = objectManager;
        this.setCommitObjectId(commitObjectId);
    }

    @Override
    public List<CommitChainItem> getParents() {
        try {
            ObjectEntity commitObjectEntity  = objectManager.read(getCommitObjectId());
            List<String> parentCommitObjectIds = CommitObjectData.parseFrom(commitObjectEntity.getData()).getParents();
            List<CommitChainItem> parents = new ArrayList<>();
            for (String parent : parentCommitObjectIds) {
                if (Objects.equals(parent, GitLite.EMPTY_OBJECT_ID)){
                    continue;
                }
                CommitChainItemLazy parentCommitChainItem = new CommitChainItemLazy(objectManager);
                parentCommitChainItem.setCommitObjectId(parent);
                parents.add(parentCommitChainItem);
            }
            return parents;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}