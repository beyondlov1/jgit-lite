package com.beyond.jgit.diff;

import java.io.IOException;

public interface CommitDiffer {
    ObjectDiffResult diff(String leftCommitObjectId, String rightCommitObjectId) throws IOException;
}
