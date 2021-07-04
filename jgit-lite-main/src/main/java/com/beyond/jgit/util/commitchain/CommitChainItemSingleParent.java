package com.beyond.jgit.util.commitchain;

import lombok.Data;

import java.io.IOException;

@Data
public class CommitChainItemSingleParent {
    private String commitObjectId;
    private CommitChainItemSingleParent parent;

    public void walk(Visitor visitor) throws IOException {
        visitor.visit(this);
        visitor.visit(parent);
    }

    public interface Visitor {
        void visit(CommitChainItemSingleParent item) throws IOException;
    }
}