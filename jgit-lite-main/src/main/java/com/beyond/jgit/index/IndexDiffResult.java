package com.beyond.jgit.index;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashSet;
import java.util.Set;

@Data
public class IndexDiffResult {
    private Set<Index.Entry> added = new HashSet<>();
    private Set<Index.Entry> removed = new HashSet<>();
    private Set<Index.Entry> updated = new HashSet<>();

    public boolean isChanged() {
        return CollectionUtils.isNotEmpty(added) || CollectionUtils.isNotEmpty(removed) || CollectionUtils.isNotEmpty(updated);
    }
}
