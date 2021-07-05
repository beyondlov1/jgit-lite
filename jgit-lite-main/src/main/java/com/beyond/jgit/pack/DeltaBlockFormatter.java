package com.beyond.jgit.pack;

import com.beyond.delta.DeltaUtils;
import com.beyond.delta.entity.Delta;
import com.beyond.jgit.util.FormatUtils;
import com.beyond.jgit.util.ObjectUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * todo: test
 */
public class DeltaBlockFormatter {

    public static int format(PackFile packFile, byte[] result, int offset) throws IOException {
        PackFile.Header header = packFile.getHeader();
        System.arraycopy(header.getFileFlag(), 0, result, 0, 4);
        System.arraycopy(header.getVersion(), 0, result, 4, 4);
        System.arraycopy(header.getEntries(), 0, result, 8, 4);
        offset += 12;
        List<DeltaBlock> deltaBlocks = packFile.getBlockList();
        for (DeltaBlock deltaBlock : deltaBlocks) {
            offset = formatOne(deltaBlock, result, offset);
        }

        PackFile.Trailer trailer = new PackFile.Trailer(DigestUtils.sha1(new ByteArrayInputStream(result, 0, offset)));
        packFile.setTrailer(trailer);
        offset += 20;
        return offset;
    }

    public static int format(List<DeltaBlock> deltaBlocks, byte[] result, int offset) {
        for (DeltaBlock deltaBlock : deltaBlocks) {
            offset = formatOne(deltaBlock, result, offset);
        }
        return offset;
    }

    public static int formatOne(DeltaBlock deltaBlock, byte[] result, int offset) {
        List<Delta> deltas = deltaBlock.getDeltas();
        int deltaByteSize = DeltaUtils.deltaByteSize(deltas);
        if (deltaBlock instanceof OfsDeltaBlock) {

            deltaBlock.setStart(offset);

            offset = FormatUtils.dynamicAddTypeAndSize(6, 3, deltaByteSize, result, offset);

            int ofs = ((OfsDeltaBlock) deltaBlock).getOfs();
            offset = FormatUtils.dynamicAddInt(ofs, result, offset);

            offset = DeltaUtils.format(deltas, result, offset);

            deltaBlock.setEnd(offset);
            return offset;

        }


        if (deltaBlock instanceof RefDeltaBlock) {
            deltaBlock.setStart(offset);

            offset = FormatUtils.dynamicAddTypeAndSize(7, 3, deltaByteSize, result, offset);

            String ref = ((RefDeltaBlock) deltaBlock).getRef();
            byte[] refBytes = ObjectUtils.hexToByteArray(ref);
            offset = FormatUtils.writeBytesTo(refBytes, result, offset);

            offset = DeltaUtils.format(deltas, result, offset);

            deltaBlock.setEnd(offset);
            return offset;
        }

        throw new RuntimeException("类型错误");
    }


    public static PackFile parse(byte[] bytes) {
        int offset = 0;
        PackFile packFile = new PackFile();
        byte[] version = new byte[4];
        System.arraycopy(bytes, 4, version, 0, 4);
        byte[] entries = new byte[4];
        System.arraycopy(bytes, 8, entries, 0, 4);
        PackFile.Header header = new PackFile.Header(version, entries);
        packFile.setHeader(header);
        offset += 12;

        packFile.setBlockList(parse(bytes, offset, bytes.length - 20 - offset));
        offset += bytes.length - 20;

        byte[] checksum = new byte[20];
        System.arraycopy(bytes, offset, checksum, 0, 20);
        PackFile.Trailer trailer = new PackFile.Trailer(checksum);
        packFile.setTrailer(trailer);
        return packFile;
    }


    public static List<DeltaBlock> parse(byte[] bytes, int offset, int len) {
        List<DeltaBlock> deltaBlocks = new ArrayList<>();
        while (offset < bytes.length) {
            DeltaBlock block = parseNextBlock(bytes, offset);
            deltaBlocks.add(block);
            offset = block.getEnd();
        }
        return deltaBlocks;
    }


    public static DeltaBlock parseNextBlock(byte[] bytes, int offset) {
        int[] typeAndSize = new int[3];
        offset = FormatUtils.readNextDynamicTypeAndSize(3, bytes, offset, typeAndSize);
        int type = typeAndSize[1];
        int size = typeAndSize[2];

        if (type == 6) {
            OfsDeltaBlock block = new OfsDeltaBlock();
            block.setStart(offset);
            int ofs = FormatUtils.readNextDynamicInt(bytes, offset);
            offset += 1;
            block.setOfs(ofs);
            List<Delta> deltas = DeltaUtils.parse(bytes, offset, size);
            offset += size;
            block.setDeltas(deltas);
            block.setEnd(offset);
            return block;
        }

        if (type == 7) {
            RefDeltaBlock block = new RefDeltaBlock();
            block.setStart(offset);
            byte[] ref = FormatUtils.readNextBytes(bytes, offset, 20);
            offset += 20;
            block.setRef(ObjectUtils.bytesToHex(ref));
            List<Delta> deltas = DeltaUtils.parse(bytes, offset, size);
            offset += size;
            block.setDeltas(deltas);
            block.setEnd(offset);
            return block;
        }

        throw new RuntimeException("类型错误");
    }
}
