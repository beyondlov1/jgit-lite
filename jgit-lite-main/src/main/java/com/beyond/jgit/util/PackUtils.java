package com.beyond.jgit.util;

import com.beyond.jgit.pack.PackInfo;
import com.beyond.jgit.pack.PackReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class PackUtils {
    public static String getIndexPath(String packPath) {
        String basePath = StringUtils.substringBefore(packPath, ".pack");
        return basePath + ".idx";
    }

    public static PackInfo readPackInfo(String objectsDir) throws IOException {
        File packInfoFile = new File(PathUtils.concat(objectsDir, "info", "packs"));
        if (packInfoFile.exists()) {
            String packInfoStr = FileUtils.readFileToString(packInfoFile, StandardCharsets.UTF_8);
            return JsonUtils.readValue(packInfoStr, PackInfo.class);
        }
        return null;
    }

    public static PackReader.PackPair getPackPair(String objectsDir, String name) {
        String packPath = PathUtils.concat(objectsDir, "pack", name);
        String packIndexPath = getIndexPath(packPath);
        return new PackReader.PackPair(new File(packIndexPath), new File(packPath));
    }

    public static void checkPackIndexCheckSum(byte[] indexBytes) throws IOException {
        byte[] computedChecksum = FormatUtils.checksum(indexBytes, 0, indexBytes.length - 20);
        byte[] checksumInFile = BytesUtils.collectByLength(indexBytes, indexBytes.length - 20, 20);
        if (!Arrays.equals(computedChecksum, checksumInFile)){
            throw new RuntimeException("idx checksum fail");
        }
    }
}
