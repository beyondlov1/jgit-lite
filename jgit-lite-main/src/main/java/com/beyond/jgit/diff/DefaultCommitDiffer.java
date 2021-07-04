package com.beyond.jgit.diff;

import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.object.ObjectManager;
import com.beyond.jgit.object.data.CommitObjectData;
import com.beyond.jgit.object.data.TreeObjectData;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultCommitDiffer implements CommitDiffer {

    private final ObjectManager leftObjectManager;
    private final ObjectManager rightObjectManager;

    public DefaultCommitDiffer(String objectDir) {
        leftObjectManager = new ObjectManager(objectDir);
        rightObjectManager = new ObjectManager(objectDir);
    }

    public DefaultCommitDiffer(String leftObjectDir, String rightObjectDir) {
        leftObjectManager = new ObjectManager(leftObjectDir);
        rightObjectManager = new ObjectManager(rightObjectDir);
    }

    @Override
    public ObjectDiffResult diff(String leftCommitObjectId, String rightCommitObjectId) throws IOException {

        if (StringUtils.equals(leftCommitObjectId, rightCommitObjectId)){
            return new ObjectDiffResult();
        }

        ObjectEntity leftCommit = leftObjectManager.read(leftCommitObjectId);
        ObjectEntity rightCommit = rightObjectManager.read(rightCommitObjectId);

        CommitObjectData leftCommitData = CommitObjectData.parseFrom(leftCommit.getData());
        CommitObjectData rightCommitData = CommitObjectData.parseFrom(rightCommit.getData());

        String leftTreeObjectId = leftCommitData.getTree();
        String rightTreeObjectId = rightCommitData.getTree();

        ObjectDiffResult objectDiffResult = diffTree(leftTreeObjectId, rightTreeObjectId);
        objectDiffResult.getLeftExtraObjectIds().add(leftCommitObjectId);
        objectDiffResult.getRightExtraObjectIds().add(rightCommitObjectId);
        return objectDiffResult;
    }

    private ObjectDiffResult diffTree(String leftTreeObjectId, String rightTreeObjectId) throws IOException {
        List<String> leftExtras = new ArrayList<>();
        List<String> rightExtras = new ArrayList<>();

        List<String> leftObjectIds = new ArrayList<>();
        List<String> rightObjectIds = new ArrayList<>();

        collectResolvedObjectIds(leftObjectManager, leftTreeObjectId, leftObjectIds);
        collectResolvedObjectIds(rightObjectManager, rightTreeObjectId, rightObjectIds);

        leftObjectIds = leftObjectIds.stream().distinct().sorted(String::compareTo).collect(Collectors.toList());
        rightObjectIds = rightObjectIds.stream().distinct().sorted(String::compareTo).collect(Collectors.toList());

        int leftIndex = 0;
        int rightIndex = 0;
        for (;;) {
            if (leftIndex < leftObjectIds.size() && rightIndex < rightObjectIds.size()){
                String leftObjectId = leftObjectIds.get(leftIndex);
                String rightObjectId = rightObjectIds.get(rightIndex);
                if (leftObjectId.equals(rightObjectId)){
                    leftIndex++;
                    rightIndex++;
                }else{
                    if (leftObjectId.compareTo(rightObjectId) < 0){
                        leftExtras.add(leftObjectId);
                        leftIndex++;
                    }else{
                        rightExtras.add(rightObjectId);
                        rightIndex++;
                    }
                }
            }else{
                if (leftIndex < leftObjectIds.size()){
                    leftExtras.add(leftObjectIds.get(leftIndex));
                    leftIndex ++;
                    continue;
                }
                if (rightIndex < rightObjectIds.size()){
                    rightExtras.add(rightObjectIds.get(rightIndex));
                    rightIndex ++;
                    continue;
                }
                break;
            }
        }
        ObjectDiffResult objectDiffResult = new ObjectDiffResult();
        objectDiffResult.getLeftExtraObjectIds().addAll(leftExtras);
        objectDiffResult.getRightExtraObjectIds().addAll(rightExtras);
        return objectDiffResult;
    }

    private void collectResolvedObjectIds(ObjectManager objectManager, String treeObjectId, List<String> objectIds) throws IOException {
        objectIds.add(treeObjectId);
        ObjectEntity tree = objectManager.read(treeObjectId);
        TreeObjectData treeData = TreeObjectData.parseFrom(tree.getData());
        List<TreeObjectData.TreeEntry> entries = treeData.getEntries();
        for (TreeObjectData.TreeEntry entry : entries) {
            if (entry.getType() == ObjectEntity.Type.blob){
                objectIds.add(entry.getObjectId());
            }
            if (entry.getType() == ObjectEntity.Type.tree){
                objectIds.add(entry.getObjectId());
                collectResolvedObjectIds(objectManager, entry.getObjectId(), objectIds);
            }
        }
    }
}
