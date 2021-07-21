package com.beyond.jgit;

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
