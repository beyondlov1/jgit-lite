package com.beyond.jgit.pack;

import com.beyond.jgit.util.FormatUtils;
import com.beyond.jgit.util.ObjectUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackIndexFormatter {

    public byte[] format(PackIndex index) throws IOException {
        byte[] result = new byte[byteSize(index)];
        // fanout
        List<PackIndex.Item> sortedItems = index.getItems().stream().sorted(Comparator.comparing(PackIndex.Item::getObjectId)).collect(Collectors.toList());
        int i = 0;
        for (PackIndex.Item item : sortedItems) {
            byte[] sha1Bytes = ObjectUtils.hexToByteArray(item.getObjectId());
            int fanoutIndex = FormatUtils.readNextInt(sha1Bytes, 0);
            FormatUtils.writeIntTo(FormatUtils.readNextInt(result, fanoutIndex * 4) + 1, result, fanoutIndex * 4);
            FormatUtils.writeIntTo(item.getOffset(), result, 256 + i * 24);
            FormatUtils.writeBytesTo(sha1Bytes, result, 256 + i * 24 + 4);
            i++;
        }
        int sum = 0;
        for (int j = 0; j < 256; j++) {
            sum += FormatUtils.readNextInt(result, j * 4);
            FormatUtils.writeIntTo(sum, result, j * 4);
        }
        byte[] packFileChecksum = index.getPackFileChecksum();
        System.arraycopy(packFileChecksum, 0, result, result.length - 20 - 20, 20);
        byte[] indexChecksum = FormatUtils.checksum(result, 0, result.length - 20);
        System.arraycopy(indexChecksum, 0, result, result.length - 20, 20);
        return result;
    }

    public int byteSize(PackIndex packIndex) {
        List<PackIndex.Item> items = packIndex.getItems();
        // fanout(256*4)+(offset+sha-1)*(4+20)+packFileChecksum+packIndexCheckSum
        return 256 * 4 + items.size() * 24 + 20 + 20;
    }

    public int indexForOffset(byte[] indexBytes, String objectId) {
        byte[] sha1Bytes = ObjectUtils.hexToByteArray(objectId);
        int fanoutIndex = FormatUtils.readNextInt(sha1Bytes, 0);
        int end = FormatUtils.readNextInt(indexBytes, fanoutIndex * 4);
        if (end == 0) {
            return -1;
        }
        int start = 0;
        if (fanoutIndex > 0) {
            start = fanoutIndex - 1;
        }
        // todo
        return 0;
    }

    private int binarySearch(byte[] indexBytes, byte[] sha1Bytes, int start, int end) {
        if (start == end) {
            return start;
        }
        int mid = (end - start) / 2 + start;
        int compare = compare(sha1Bytes, 0, indexBytes, 256 * 4 + mid * 24 + 4, 20);
        if (compare < 0) {
            return binarySearch(indexBytes, sha1Bytes, start, mid);
        } else if (compare > 0) {
            return binarySearch(indexBytes, sha1Bytes, mid, end);
        } else {
            return mid;
        }
    }

    public int compare(byte[] bytes1, int bytes1Offset, byte[] bytes2, int bytes2Offset, int len) {
//        byte[] tmp = new byte[20];
//        System.arraycopy(bytes2, bytes2Offset, tmp, 0, 20);
//        System.out.println(Arrays.toString(bytes1));
//        System.out.println(Arrays.toString(tmp));
        for (int i = 0; i < len; i++) {
            if (bytes1[bytes1Offset+i] < bytes2[bytes2Offset+i]) {
                return -1;
            }
            if (bytes1[bytes1Offset+i] > bytes2[bytes2Offset+i]) {
                return 1;
            }
        }
        return 0;
    }


    public static void main(String[] args) {
        byte[] a = new byte[256 * 4 + 24 * 30];
        byte[] items = new byte[24 * 30];
        addItem(new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, 0, items);
        addItem(new byte[]{1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, 1, items);
        addItem(new byte[]{1, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, 2, items);
        addItem(new byte[]{1, 2, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, 3, items);
        addItem(new byte[]{1, 2, 3, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, 4, items);
        addItem(new byte[]{1, 2, 3, 4, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1}, 5, items);
        addItem(new byte[]{1, 2, 3, 4, 1, 1, 1, 1, 1, 1, 1, 5, 1, 1, 1, 1, 1, 1, 1, 1}, 6, items);
        addItem(new byte[]{1, 2, 3, 4, 1, 1, 1, 1, 1, 1, 1, 5, 1, 1, 1, 1, 1, 1, 1, 7}, 7, items);
        addItem(new byte[]{1, 2, 3, 4, 1, 1, 1, 1, 1, 1, 1, 5, 1, 1, 1, 1, 1, 1, 1, 8}, 8, items);
        addItem(new byte[]{1, 2, 3, 4, 1, 1, 1, 1, 1, 1, 1, 5, 1, 1, 1, 1, 1, 1, 1, 9}, 9, items);
        System.arraycopy(items, 0, a, 256 * 4, items.length);
        PackIndexFormatter packIndexFormatter = new PackIndexFormatter();
        int i = packIndexFormatter.binarySearch(a, new byte[]{1, 2, 3, 4, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, 0, 10);
        System.out.println(i);
    }


    private static void addItem(byte[] item, int addIndex, byte[] items) {
        for (int i = 0; i < item.length; i++) {
            items[24 * addIndex + 4 + i] = item[i];
        }
    }
}
