package com.beyond.jgit.pack;

import lombok.Data;

import java.util.List;

/**
 * @author chenshipeng
 * @date 2021/07/05
 */
@Data
public class PackFile {
    private Header header;
    private List<DeltaBlock> blockList;
    private Trailer trailer;


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
