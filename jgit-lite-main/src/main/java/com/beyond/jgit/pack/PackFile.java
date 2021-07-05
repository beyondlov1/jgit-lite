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
    }

    @Data
    public static class Trailer{
        private byte[] checksum;

        public Trailer(byte[] checksum) {
            this.checksum = checksum;
        }
    }
}
