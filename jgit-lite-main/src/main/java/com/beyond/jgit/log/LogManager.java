package com.beyond.jgit.log;

import com.beyond.jgit.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static com.beyond.jgit.GitLite.EMPTY_OBJECT_ID;

public class LogManager {
    private String logPath;
    private String logLockPath;

    public LogManager(String logPath) {
        this.logPath = logPath;
        this.logLockPath = logPath + ".lock";
    }

    public static List<LogItem> getLogsFromFile(File logFile) throws IOException {
        if (!logFile.exists()) {
            return null;
        }
        String json = FileUtils.readFileToString(logFile, StandardCharsets.UTF_8);
        return JsonUtils.readValue(json, new TypeReference<List<LogItem>>() {
        });
    }


    public void appendToLock(LogItem logItem) throws IOException {
        LogItem lastLogItem = getLastLogItem();
        String parentCommitObjectId = EMPTY_OBJECT_ID;
        if (lastLogItem != null && lastLogItem.getCommitObjectId() != null) {
            parentCommitObjectId = lastLogItem.getCommitObjectId();
        }
        if (StringUtils.equals(parentCommitObjectId, logItem.getCommitObjectId())) {
            return;
        }

        List<LogItem> logs = getLogs();
        if (logs == null) {
            logs = new ArrayList<>();
        }
        logs.add(logItem);
        FileUtils.writeStringToFile(new File(logLockPath), JsonUtils.writeValueAsString(logs), StandardCharsets.UTF_8);
    }

    // todo:file lock
    public void appendToLock(String commitObjectId, String committerName, String committerEmail, String message, long commitTime) throws IOException {
        LogItem lastLogItem = getLastLogItem();
        String parentCommitObjectId = EMPTY_OBJECT_ID;
        if (lastLogItem != null && lastLogItem.getCommitObjectId() != null) {
            parentCommitObjectId = lastLogItem.getCommitObjectId();
        }
        if (StringUtils.equals(parentCommitObjectId, commitObjectId)) {
            return;
        }
        LogItem logItem = new LogItem(parentCommitObjectId, commitObjectId, committerName, committerEmail, message, commitTime);
        appendToLock(logItem);
    }

    public List<LogItem> getLogs() throws IOException {
        File logFile = new File(logPath);
        File logLockFile = new File(logLockPath);
        if (logLockFile.exists()) {
            logFile = logLockFile;
        }
        if (!logFile.exists()) {
            return null;
        }
        String json = FileUtils.readFileToString(logFile, StandardCharsets.UTF_8);
        return JsonUtils.readValue(json, new TypeReference<List<LogItem>>() {
        });
    }

    public LogItem getLastLogItem() throws IOException {
        List<LogItem> logs = getLogs();
        if (logs == null || logs.size() == 0) {
            return null;
        }
        return logs.get(logs.size() - 1);
    }

    public void writeToLock(List<LogItem> logs) throws IOException {
        FileUtils.writeStringToFile(new File(logLockPath), JsonUtils.writeValueAsString(logs), StandardCharsets.UTF_8);
    }

    public void lock() throws IOException {
        if (new File(logLockPath).exists()) {
            throw new RuntimeException("logs lock exists");
        }
        FileUtils.writeStringToFile(new File(logLockPath), JsonUtils.writeValueAsString(getLogs()), StandardCharsets.UTF_8);
    }

    public void rollback() throws IOException {
        FileUtils.deleteQuietly(new File(logLockPath));
    }

    public void commit() throws IOException {
        Files.move(new File(logLockPath).toPath(), new File(logPath).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
