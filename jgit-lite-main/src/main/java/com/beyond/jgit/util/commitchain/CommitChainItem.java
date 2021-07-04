package com.beyond.jgit.util.commitchain;

import com.beyond.jgit.GitLite;
import lombok.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class CommitChainItem {
    private String commitObjectId;
    private List<CommitChainItem> parents = new ArrayList<>();

    public void walk(Visitor visitor) throws IOException {
        visitor.visit(this);
        for (CommitChainItem parent : getParents()) {
            visitor.visit(parent);
        }
    }

    public interface Visitor {
        void visit(CommitChainItem item) throws IOException;
    }

    public boolean isEmptyObject(){
        return Objects.equals(commitObjectId, GitLite.EMPTY_OBJECT_ID);
    }
}