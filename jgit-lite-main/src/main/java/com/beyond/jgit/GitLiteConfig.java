package com.beyond.jgit;

import com.beyond.jgit.util.PathUtils;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


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

    public static GitLiteConfig simpleConfig(String localDir, String committerName, String committerEmail){
        GitLiteConfig config = new GitLiteConfig();
        config.setLocalDir(localDir);
        config.setGitDir(PathUtils.concat(config.getLocalDir(),".git"));
        config.setHeadPath(PathUtils.concat(config.getGitDir(), "HEAD"));
        config.setIndexPath(PathUtils.concat(config.getGitDir(), "index.json"));
        config.setObjectsDir(PathUtils.concat(config.getGitDir(), "objects"));
        config.setRefsDir(PathUtils.concat(config.getGitDir(), "refs"));
        config.setRefsRemotesDir(PathUtils.concat(config.getGitDir(), "refs", "remotes"));
        config.setRefsHeadsDir(PathUtils.concat(config.getGitDir(), "refs", "heads"));
        config.setLogsDir(PathUtils.concat(config.getGitDir(), "logs"));
        config.setLogsHeadsDir(PathUtils.concat(config.getLogsDir(), "heads"));
        config.setLogsRemotesDir(PathUtils.concat(config.getLogsDir(), "remotes"));
        config.setObjectPackDir(PathUtils.concat(config.getObjectsDir(),"pack"));
        config.setObjectInfoDir(PathUtils.concat(config.getObjectsDir(),"info"));
        config.setCommitterName(committerName);
        config.setCommitterEmail(committerEmail);
        return config;
    }

    public GitLiteConfig addRemote(String remoteName,String remoteUrl,String remoteUserName, String remotePassword){
        RemoteConfig remoteConfig = new RemoteConfig(remoteName, remoteUrl, remoteUserName, remotePassword);
        remoteConfig.setRemoteTmpDir(PathUtils.concat(gitDir,"tmp"));
        remoteConfigs.add(remoteConfig);
        return this;
    }

    public GitLiteConfig addRemote(String remoteName,String remoteUrl){
        RemoteConfig remoteConfig = new RemoteConfig(remoteName, remoteUrl);
        remoteConfig.setRemoteTmpDir(PathUtils.concat(gitDir,"tmp","remotes"));
        remoteConfigs.add(remoteConfig);
        return this;
    }

    public GitLite build(){
        return new GitLite(this);
    }

    @Data
    public static class RemoteConfig {
        private String remoteName;
        private String remoteUrl;
        private String remoteUserName;
        private String remotePassword;
        private String remoteTmpDir;

        public RemoteConfig(String remoteName, String remoteUrl) {
            this.remoteName = remoteName;
            this.remoteUrl = remoteUrl;
        }

        public RemoteConfig(String remoteName, String remoteUrl, String remoteUserName, String remotePassword) {
            this.remoteName = remoteName;
            this.remoteUrl = remoteUrl;
            this.remoteUserName = remoteUserName;
            this.remotePassword = remotePassword;
        }
    }
}
