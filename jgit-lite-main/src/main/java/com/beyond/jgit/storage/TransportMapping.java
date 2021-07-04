package com.beyond.jgit.storage;

import com.beyond.jgit.util.JsonUtils;
import com.beyond.jgit.util.ObjectUtils;
import lombok.Data;

@Data
public class TransportMapping {
    private String localPath;
    private String remotePath;

    public static TransportMapping of(String localPath, String remotePath) {
        TransportMapping transportMapping = new TransportMapping();
        transportMapping.setLocalPath(localPath);
        transportMapping.setRemotePath(remotePath);
        return transportMapping;
    }

    public String toSha1hash(){
        return ObjectUtils.sha1hash(JsonUtils.writeValueAsBytes(this));
    }
}
