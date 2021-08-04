package com.beyond.jgit;

import com.beyond.jgit.util.JsonUtils;
import com.beyond.jgit.util.PathUtils;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Data
public class GitLiteConfig {
    private String localDir;
    private String headPath;
    private String indexPath;
    private String gitDir;
    private String objectsDir;
    private String refsDir;
    private String refsRemotesDir;
    private String refsHeadsDir;
    private String logsDir;
    private String logsRemotesDir;
    private String logsHeadsDir;

    private String committerName;
    private String committerEmail;

    private List<RemoteConfig> remoteConfigs = new ArrayList<>();

    private String objectPackDir;
    private String objectInfoDir;

    private String ignorePath;

    public static GitLiteConfig simpleConfig(String localDir, String committerName, String committerEmail) {
        GitLiteConfig config = new GitLiteConfig();
        config.setLocalDir(localDir);
        config.setGitDir(PathUtils.concat(config.getLocalDir(), ".git"));
        config.setHeadPath(PathUtils.concat(config.getGitDir(), "HEAD"));
        config.setIndexPath(PathUtils.concat(config.getGitDir(), "index.json"));
        config.setObjectsDir(PathUtils.concat(config.getGitDir(), "objects"));
        config.setRefsDir(PathUtils.concat(config.getGitDir(), "refs"));
        config.setRefsRemotesDir(PathUtils.concat(config.getGitDir(), "refs", "remotes"));
        config.setRefsHeadsDir(PathUtils.concat(config.getGitDir(), "refs", "heads"));
        config.setLogsDir(PathUtils.concat(config.getGitDir(), "logs"));
        config.setLogsHeadsDir(PathUtils.concat(config.getLogsDir(), "heads"));
        config.setLogsRemotesDir(PathUtils.concat(config.getLogsDir(), "remotes"));
        config.setObjectPackDir(PathUtils.concat(config.getObjectsDir(), "pack"));
        config.setObjectInfoDir(PathUtils.concat(config.getObjectsDir(), "info"));
        config.setCommitterName(committerName);
        config.setCommitterEmail(committerEmail);
        config.setIgnorePath(PathUtils.concat(localDir, ".gitignore"));
        return config;
    }

    public static GitLiteConfig readFrom(String localDir) throws IOException {
        return JsonUtils.readValue(new File(PathUtils.concat(localDir, ".git", "config.json")), GitLiteConfig.class);
    }

    public GitLiteConfig upsertRemote(String remoteName, String remoteUrl, String remoteUserName, String remotePassword) {
        RemoteConfig remoteConfig = new RemoteConfig(remoteName, remoteUrl, remoteUserName, remotePassword);
        remoteConfig.setRemoteTmpDir(PathUtils.concat(gitDir, "tmp", "remotes"));
        this.upsertRemote(remoteConfig);
        return this;
    }

    public GitLiteConfig upsertRemote(String remoteName, String remoteUrl) {
        RemoteConfig remoteConfig = new RemoteConfig(remoteName, remoteUrl);
        remoteConfig.setRemoteTmpDir(PathUtils.concat(gitDir, "tmp", "remotes"));
        this.upsertRemote(remoteConfig);
        return this;
    }

    public boolean containsRemote(RemoteConfig remoteConfig) {
        return containsRemote(remoteConfig.getRemoteName());
    }

    public boolean containsRemote(String remoteName) {
        return remoteConfigs.stream().map(RemoteConfig::getRemoteName).collect(Collectors.toSet()).contains(remoteName);
    }

    public void upsertRemote(RemoteConfig remoteConfig) {
        if (!this.containsRemote(remoteConfig)) {
            remoteConfigs.add(remoteConfig);
        } else {
            remoteConfigs.replaceAll(x -> {
                if (StringUtils.equals(x.getRemoteName(), remoteConfig.getRemoteName())) {
                    return remoteConfig;
                }
                return x;
            });
        }
    }

    public GitLiteConfig save() throws IOException {
        String configJson = JsonUtils.writeValueAsString(this);
        FileUtils.writeStringToFile(new File(PathUtils.concat(getGitDir(), "config.json")), configJson, StandardCharsets.UTF_8);
        return this;
    }

    public GitLite build() {
        return new GitLite(this);
    }

    @Data
    public static class RemoteConfig {
        private String remoteName;
        private String remoteUrl;
        private String remoteUserName;
        private String remotePassword;
        private String remoteTmpDir;

        @Deprecated
        public RemoteConfig(){

        }

        public RemoteConfig(String remoteName, String remoteUrl) {
            this.remoteName = remoteName;
            this.remoteUrl = remoteUrl;
        }

        public RemoteConfig(String remoteName, String remoteUrl, String remoteUserName, String remotePassword) {
            this.remoteName = remoteName;
            this.remoteUrl = remoteUrl;
            this.remoteUserName = StringUtils.trimToNull(remoteUserName);
            this.remotePassword = StringUtils.trimToNull(remotePassword);
        }

    }
}
