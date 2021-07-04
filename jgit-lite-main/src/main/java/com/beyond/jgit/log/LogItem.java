package com.beyond.jgit.log;

import lombok.Data;

@Data
public class LogItem {
    private String parentCommitObjectId;
    private String commitObjectId;
    private String committerName;
    private String committerEmail;
    private String message;
    private long mtime;

    public LogItem() {
    }

    public LogItem(String parentCommitObjectId, String commitObjectId, String committerName, String committerEmail, String message, long mtime) {
        this.parentCommitObjectId = parentCommitObjectId;
        this.commitObjectId = commitObjectId;
        this.committerName = committerName;
        this.committerEmail = committerEmail;
        this.message = message;
        this.mtime = mtime;
    }
}
