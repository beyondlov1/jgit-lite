package com.beyond.jgit.util.commitchain;

import com.beyond.jgit.GitLite;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public class CommitChainItem {
    private String commitObjectId;
    private List<CommitChainItem> parents = new ArrayList<>();

    public void dfsWalk(Visitor visitor) throws IOException {
        visitor.visit(this);
        for (CommitChainItem parent : getParents()) {
            parent.dfsWalk(visitor);
        }
    }

    public void bfsWalk(Visitor visitor, Runnable levelEnd) throws IOException {
        bfsWalkInternal(visitor, levelEnd,Collections.singletonList(this));
    }

    private void bfsWalkInternal(Visitor visitor, Runnable levelEnd, List<CommitChainItem> level) throws IOException {
        if (CollectionUtils.isEmpty(level)){
            return;
        }
        for (CommitChainItem commitChainItem : level) {
            visitor.visit(commitChainItem);
        }
        levelEnd.run();
        bfsWalkInternal(visitor,levelEnd, level.stream().flatMap(x->x.getParents().stream()).collect(Collectors.toList()));
    }

    public void print() throws IOException {
        StringBuilder sb = new StringBuilder();

        bfsWalk(item -> {
            sb.append(item.getCommitObjectId());
            sb.append("->");
            sb.append(item.getParents().stream().map(CommitChainItem::getCommitObjectId).collect(Collectors.joining("-")));
            sb.append("   ");
        }, () -> sb.append("\n"));
        System.out.println(sb);
    }

    public interface Visitor {
        void visit(CommitChainItem item) throws IOException;
    }

    public boolean isEmptyObject(){
        return Objects.equals(commitObjectId, GitLite.EMPTY_OBJECT_ID);
    }
}