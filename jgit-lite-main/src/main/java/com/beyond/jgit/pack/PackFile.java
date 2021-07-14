package com.beyond.jgit.pack;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author chenshipeng
 * @date 2021/07/05
 */
@Data
public class PackFile {
    private Header header;
    private List<Block> blockList;
    private Trailer trailer;

    public List<PackFile> split(int limit) {
        List<List<Block>> blockGroups = new ArrayList<>();
        List<Block> reversedBlockList = new ArrayList<>(blockList);
        int sumSize = 0;
        List<Block> currBlockGroup = new LinkedList<>();
        for (Block block : reversedBlockList) {
            if (sumSize <= limit){
                currBlockGroup.add(0,block);
            }else{
                blockGroups.add(currBlockGroup);
                currBlockGroup = new LinkedList<>();
                currBlockGroup.add(0, block);
                blockGroups.add(currBlockGroup);
                sumSize = 0;
            }
            sumSize += PackFileFormatter.size(block);
        }

        List<PackFile> packFiles = new ArrayList<>();
        for (List<Block> blockGroup : blockGroups) {
            PackFile packFile = new PackFile();
            packFile.setHeader(new Header(1, blockGroup.size()));
            packFile.setBlockList(blockGroup);
            packFiles.add(packFile);
        }

        // todo: 切换文件时的block类型变化
        return packFiles;
    }


    @Data
    public static class Header{
        private byte[] fileFlag = new byte[]{'P','A','C','K'};
        private byte[] version;
        private byte[] entries;

        public Header(byte[] version, byte[] entries) {
            this.version = version;
            this.entries = entries;
        }

        public Header(int version, int entries) {
            this.version = int2Bytes(version);
            this.entries = int2Bytes(entries);
        }

        public byte[] int2Bytes(int n){
            byte[] result = new byte[4];
            result[3] = (byte) (n & 0xff);
            result[2] = (byte) (n >> 8 & 0xff);
            result[1] = (byte) (n >> 16 & 0xff);
            result[0] = (byte) (n >> 24 & 0xff);
            return result;
        }

    }

    @Data
    public static class Trailer{
        private byte[] checksum;

        public Trailer(byte[] checksum) {
            this.checksum = checksum;
        }
    }
}
