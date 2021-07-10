package com.beyond.jgit.pack;

import com.beyond.delta.DeltaUtils;
import com.beyond.delta.entity.Delta;
import com.beyond.jgit.object.ObjectEntity;
import com.beyond.jgit.util.FormatUtils;
import com.beyond.jgit.util.ObjectUtils;
import com.beyond.jgit.util.ZlibCompression;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * todo: test
 */
public class BlockFormatter {

    @SneakyThrows
    public static int size(Block block)  {
        if (block instanceof BaseBlock) {
            byte[] compressBytes = ZlibCompression.compressBytes(((BaseBlock) block).getContent());
            return FormatUtils.dynamicByteSizeOfTypeAndSize(3,compressBytes.length) + compressBytes.length;
        }
        if (block instanceof DeltaBlock) {
            List<Delta> deltas = ((DeltaBlock) block).getDeltas();
            if (block instanceof RefDeltaBlock) {
                return FormatUtils.dynamicByteSizeOfTypeAndSize(3, DeltaUtils.deltaByteSize(deltas)) + 20 + DeltaUtils.deltaByteSize(deltas);
            }
            if (block instanceof OfsDeltaBlock) {
                return FormatUtils.dynamicByteSizeOfTypeAndSize(3, DeltaUtils.deltaByteSize(deltas)) + FormatUtils.dynamicByteSize(((OfsDeltaBlock) block).getOfs()) + DeltaUtils.deltaByteSize(deltas);
            }
        }
        throw new RuntimeException("错误类型");
    }

    public static int size(PackFile packFile) {
        List<Block> blockList = packFile.getBlockList();
        int blockListSize = blockList.stream().map(BlockFormatter::size).reduce(Integer::sum).orElse(0);
        return 12 + blockListSize + 20;
    }

    public static PackIndex format(PackFile packFile, byte[] result, int offset) throws IOException {
        PackFile.Header header = packFile.getHeader();
        System.arraycopy(header.getFileFlag(), 0, result, 0, 4);
        System.arraycopy(header.getVersion(), 0, result, 4, 4);
        System.arraycopy(header.getEntries(), 0, result, 8, 4);
        offset += 12;

        List<Block> blocks = packFile.getBlockList();
        offset = format(blocks, result, offset);

        PackIndex packIndex = PackIndex.newInstance();
        for (Block block : blocks) {
            packIndex.add(block.getObjectId(), block.getStart());
        }

        PackFile.Trailer trailer = new PackFile.Trailer((FormatUtils.checksum(result, 0, offset)));
        packFile.setTrailer(trailer);
        FormatUtils.writeBytesTo(trailer.getChecksum(), result, offset);
        return packIndex;
    }


    public static int format(List<Block> blocks, byte[] result, int offset) throws IOException {
        for (Block block : blocks) {
            if (block instanceof BaseBlock) {
                offset = formatOneBase((BaseBlock) block, result, offset);
            }
            if (block instanceof DeltaBlock) {
                offset = formatOneDelta((DeltaBlock) block, result, offset);
            }
        }
        return offset;
    }

    public static int formatOneBase(BaseBlock baseBlock, byte[] result, int offset) throws IOException {
        baseBlock.setStart(offset);
        byte[] compressBytes = ZlibCompression.compressBytes(baseBlock.getContent());
        int length = compressBytes.length;
        offset = FormatUtils.dynamicAddTypeAndSize(baseBlock.getType().getVal(), 3, length, result, offset);
        System.arraycopy(compressBytes, 0, result, offset, length);
        baseBlock.setEnd(offset + length);
        return offset + length;
    }

    public static int formatOneDelta(DeltaBlock deltaBlock, byte[] result, int offset) {
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


    public static PackFile parse(byte[] bytes) throws IOException {
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
        offset += bytes.length - 20 - offset;

        byte[] currChecksum = FormatUtils.checksum(bytes, 0, offset);

        byte[] checksumSaved = new byte[20];
        System.arraycopy(bytes, offset, checksumSaved, 0, 20);

        if (!Arrays.equals(currChecksum, checksumSaved)) {
            throw new RuntimeException("checksum error");
        }

        PackFile.Trailer trailer = new PackFile.Trailer(checksumSaved);
        packFile.setTrailer(trailer);
        return packFile;
    }


    public static List<Block> parse(byte[] bytes, int offset, int len) throws IOException {
        List<Block> blocks = new ArrayList<>();
        int end = offset + len;
        while (offset < end) {
            Block block = parseNextBlock(bytes, offset);
            blocks.add(block);
            offset = block.getEnd();
        }
        return blocks;
    }


    public static Block parseNextBlock(byte[] bytes, int offset) throws IOException {
        int[] typeAndSize = new int[3];
        offset = FormatUtils.readNextDynamicTypeAndSize(3, bytes, offset, typeAndSize);
        int type = typeAndSize[1];
        int size = typeAndSize[2];

        if (type == 1 || type == 2 || type == 3) {
            BaseBlock block = new BaseBlock();
            block.setStart(offset);
            byte[] content = new byte[size];
            System.arraycopy(bytes, offset, content, 0, size);
            block.setContent(ZlibCompression.decompressBytes(content));
            offset += size;
            block.setType(ObjectEntity.Type.of(type));
            block.setEnd(offset);
            return block;
        }

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

    public static void main(String[] args) throws IOException {
        byte[] target = "abcdefghigklmnopqrstuvwxyz789defghigklmiidfad".getBytes(StandardCharsets.UTF_8);
        byte[] base = "e34abcdefghigkl123mnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        PackFile packFile = new PackFile();
        PackFile.Header header = new PackFile.Header(1, 1);
        packFile.setHeader(header);

        List<Block> blockList = new ArrayList<>();
        List<Delta> deltas = DeltaUtils.makeDeltas(target, base);
        RefDeltaBlock deltaBlock = new RefDeltaBlock(ObjectUtils.sha1hash(new byte[]{32, 23, 5}), deltas,ObjectUtils.sha1hash(new byte[]{1, 2, 3, 5}));
        blockList.add(deltaBlock);
        packFile.setBlockList(blockList);

        System.out.println(DeltaUtils.deltaByteSize(deltas));
        byte[] c = new byte[90];
        FormatUtils.dynamicAddTypeAndSize(7, 3, 25, c, 0);
        System.out.println(Arrays.toString(c));
        System.out.println(Arrays.toString(DeltaUtils.format(deltas)));
        System.out.println(DeltaUtils.parse(new byte[]{12, 0, 3, 12, 12, 18, 69, 24, 121, 122, 55, 56, 57, 9, 29, 6, 71, 38, 109, 105, 105, 100, 102, 97, 100}));

        byte[] a = new byte[size(packFile)];
        PackIndex packIndex = format(packFile, a, 0);
        System.out.println(packIndex);
        System.out.println(a.length);
        System.out.println(Arrays.toString(a));
        PackFile packFile1 = parse(a);
        System.out.println(packFile);
        System.out.println(packFile1);
    }
}
