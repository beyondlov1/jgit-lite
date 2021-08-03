package com.beyond.jgit.pack;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class PackWriter {
    public static void write(PackFile packFile, File packDataFile, File packIndexFile) throws IOException {
        byte[] packFileBytes = new byte[PackFileFormatter.size(packFile)];
        PackIndex packIndex = PackFileFormatter.format(packFile, packFileBytes, 0);
        byte[] packIndexBytes = PackIndexFormatter.format(packIndex);
        FileUtils.writeByteArrayToFile(packDataFile, packFileBytes);
        FileUtils.writeByteArrayToFile(packIndexFile, packIndexBytes);
        PackCache.clear();
    }
}
