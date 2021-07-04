package com.beyond.jgit.index;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexDiffer {
    public static IndexDiffResult diff(Index index, Index base) {

        if (index == null && base == null){
            return new IndexDiffResult();
        }

        if (index == null){
            IndexDiffResult result = new IndexDiffResult();
            result.setRemoved(new HashSet<>(base.getEntries()));
            return result;
        }

        if (base == null){
            IndexDiffResult result = new IndexDiffResult();
            result.setAdded(new HashSet<>(index.getEntries()));
            return result;
        }

        IndexDiffResult result = new IndexDiffResult();
        Set<Index.Entry> added = result.getAdded();
        Set<Index.Entry> removed = result.getRemoved();
        Set<Index.Entry> updated = result.getUpdated();

        List<Index.Entry> entries = index.getEntries().stream().sorted(Comparator.comparing(Index.Entry::getPath)).collect(Collectors.toList());
        List<Index.Entry> baseEntries = base.getEntries().stream().sorted(Comparator.comparing(Index.Entry::getPath)).collect(Collectors.toList());

        int leftIndex = 0;
        int rightIndex = 0;
        for (;;) {
            if (leftIndex < entries.size() && rightIndex < baseEntries.size()) {
                Index.Entry left = entries.get(leftIndex);
                Index.Entry right = baseEntries.get(rightIndex);
                if (left.getPath().equals(right.getPath())) {
                    if (!left.getObjectId().equals(right.getObjectId())) {
                        updated.add(left);
                    }
                    leftIndex++;
                    rightIndex++;
                } else {
                    if (left.getPath().compareTo(right.getPath()) < 0) {
                        added.add(left);
                        leftIndex++;
                    } else {
                        removed.add(right);
                        rightIndex++;
                    }
                }
            } else {
                if (leftIndex < entries.size()) {
                    added.add(entries.get(leftIndex));
                    leftIndex++;
                    continue;
                }
                if (rightIndex < baseEntries.size()) {
                    removed.add(baseEntries.get(rightIndex));
                    rightIndex++;
                    continue;
                }
                break;
            }
        }

        return result;
    }
}
