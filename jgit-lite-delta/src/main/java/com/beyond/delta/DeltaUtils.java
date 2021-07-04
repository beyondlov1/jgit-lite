package com.beyond.delta;

import com.beyond.delta.entity.*;
import com.beyond.delta.entity.Formatter;
import com.google.common.hash.Hashing;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class DeltaUtils {

    private static final int NHASH = 3;

    public static List<Delta> makeDeltas(byte[] target, byte[] base) {
        MultiValuedMap<String, OriginChunk> originChunkMap = new ArrayListValuedHashMap<>();
        byte[] tmp = new byte[NHASH];
        for (int i = 0; i < base.length; i++) {
            tmp[i % NHASH] = base[i];
            if (i % NHASH == NHASH - 1) {
                OriginChunk chunk = new OriginChunk();
                chunk.setIndex(i / NHASH);
                chunk.setRange(new Range(i / NHASH * NHASH, i / NHASH * NHASH + NHASH));
                chunk.setHash(Hashing.goodFastHash(32).hashBytes(tmp).toString());
                originChunkMap.put(chunk.getHash(), chunk);
            }
        }
        System.out.println(originChunkMap);

        List<Slice> result = new ArrayList<>();

        Slice[] sliceArray = new Slice[NHASH];
        Slice[] lastSliceArray = new Slice[NHASH];
        for (int i = 0; i < target.length / NHASH - 1; i++) {

            for (int j = 0; j < NHASH; j++) {
                int sliceStart = i * NHASH + j;
                int sliceEnd = i * NHASH + j + NHASH;

                Slice lastSlice = lastSliceArray[j];
                if (sliceEnd >= target.length) {
                    sliceArray[j] = null;
                    continue;
                }

                byte[] sliceBytes = new byte[NHASH];
                System.arraycopy(target, sliceStart, sliceBytes, 0, NHASH);
                Slice slice = new Slice();
                slice.setStart(sliceStart);
                slice.setEnd(sliceEnd);
                if (lastSlice != null) {
                    lastSlice.setNext(slice);
                    slice.setPre(lastSlice);
                }

                Collection<OriginChunk> originChunks = originChunkMap.get(Hashing.goodFastHash(32).hashBytes(sliceBytes).toString());
                List<Slice.Candidate> candidates = originChunks.stream().map(x -> {
                    Slice.Candidate candidate = new Slice.Candidate();
                    candidate.setOriginChunk(x);

                    List<Slice.Candidate> lastCandidates;
                    if (lastSlice != null) {
                        lastCandidates = lastSlice.getCandidates();
                    } else {
                        lastCandidates = Collections.emptyList();
                    }

                    Slice.Candidate chainedPreCandidate = null;
                    int index = x.getIndex();
                    for (Slice.Candidate lastCandidate : lastCandidates) {
                        OriginChunk candidateOriginChunk = lastCandidate.getOriginChunk();
                        if (candidateOriginChunk != null) {
                            if (candidateOriginChunk.getIndex() + 1 == index) {
                                chainedPreCandidate = lastCandidate;
                                break;
                            }
                        }
                    }
                    if (chainedPreCandidate == null) {
                        candidate.setPre(null);
                        candidate.setTotalLength(NHASH);
                    } else {
                        candidate.setPre(chainedPreCandidate);
                        chainedPreCandidate.setNext(candidate);
                        candidate.setTotalLength(NHASH + chainedPreCandidate.getTotalLength());
                    }

                    candidate.setTargetSlice(slice);
                    return candidate;
                }).collect(Collectors.toList());
                slice.setCandidates(candidates);

                sliceArray[j] = slice;
            }

            // 判断是否连续
            boolean coiled = false;
            if (i == 0) {
                coiled = true;
            } else {
                for (Slice slice : sliceArray) {
                    for (Slice.Candidate candidate : slice.getCandidates()) {
                        if (candidate.getPre() != null) {
                            coiled = true;
                            break;
                        }
                    }
                    if (coiled) {
                        break;
                    }
                }
            }

            if (!coiled) {
                // 找到上一个的最长链
                Slice slice = getLongestChainSlice(lastSliceArray);

                // 把pre上的最长对应的候选选出来
                // 如果上一个选出来的最长的候选为空，则上一个SliceArray全都没匹配到任何字节, 自然也就不可能是copyRange
                if (CollectionUtils.isNotEmpty(slice.getCandidates())) {
                    result.add(slice);
                }

                // 做个标记， 但是没有用到
                for (Slice s : sliceArray) {
                    s.setInterrupted(true);
                }
            }

            // curr to last
            Slice[] t = sliceArray;
            sliceArray = lastSliceArray;
            lastSliceArray = t;
            // reset sliceArray
            Arrays.fill(sliceArray, null);
        }

        Slice endLongestChainSlice = getLongestChainSlice(lastSliceArray);
        if (CollectionUtils.isNotEmpty(endLongestChainSlice.getCandidates())) {
            result.add(endLongestChainSlice);
        }

        for (Slice slice : result) {
            List<Slice.Candidate> candidates = slice.getCandidates();
            Slice.Candidate bestCandidate = candidates.stream().max(Comparator.comparingInt(Slice.Candidate::getTotalLength)).orElse(null);
            slice.setBestCandidate(bestCandidate);
        }


        List<CopyRangeDelta> copyRangeDeltas = new LinkedList<>();
        int lastSliceEnd = 0;
        for (Slice slice : result) {
            Slice.Candidate bestCandidate = slice.getBestCandidate();

            Range targetRange = bestCandidate.mergeTargetRange(lastSliceEnd);

            // 根据target的长度确定origin长度, 防止交叉
            int originEnd = bestCandidate.getOriginChunk().getRange().getEnd();
            Range originRange = new Range(originEnd - (targetRange.getEnd() - targetRange.getStart()), originEnd);
            copyRangeDeltas.add(new CopyRangeDelta(originRange, targetRange));

            lastSliceEnd = slice.getEnd();
        }

        List<Delta> deltas = new LinkedList<>();
        int currIndex = 0;
        for (CopyRangeDelta copyRangeDelta : copyRangeDeltas) {
            Range targetRange = copyRangeDelta.getTargetRange();
            if (currIndex == targetRange.getStart()) {
                deltas.add(copyRangeDelta);
            } else {
                Range range = new Range(currIndex, targetRange.getStart());
                InsertLiterDelta insertLiterDelta = new InsertLiterDelta(range, range.read(target));
                deltas.add(insertLiterDelta);
                deltas.add(copyRangeDelta);
            }
            currIndex = targetRange.getEnd();
        }
        if (currIndex < target.length) {
            Range range = new Range(currIndex, target.length);
            InsertLiterDelta insertLiterDelta = new InsertLiterDelta(range, range.read(target));
            deltas.add(insertLiterDelta);
        }

        return deltas;
    }

    private static Slice getLongestChainSlice(Slice[] sliceArray) {
        return Arrays.stream(sliceArray).max((o1, o2) -> {
            List<Slice.Candidate> candidates1 = o1.getCandidates();
            Integer maxLengthPerSlice1 = candidates1.stream().map(Slice.Candidate::getTotalLength).max(ComparatorUtils.naturalComparator()).orElse(0);

            List<Slice.Candidate> candidates2 = o2.getCandidates();
            Integer maxLengthPerSlice2 = candidates2.stream().map(Slice.Candidate::getTotalLength).max(ComparatorUtils.naturalComparator()).orElse(0);

            return maxLengthPerSlice1 - maxLengthPerSlice2;
        }).orElse(null);
    }

    public static byte[] applyDeltas(List<Delta> deltas, byte[] base) {
        int size = deltas.get(deltas.size() - 1).getTargetRange().getEnd();
        byte[] result = new byte[size];
        int offset = 0;
        for (Delta delta : deltas) {
            if (delta instanceof CopyRangeDelta) {
                byte[] read = ((CopyRangeDelta) delta).getOriginRange().read(base);
                System.arraycopy(read, 0, result, offset, read.length);
            }
            if (delta instanceof InsertLiterDelta) {
                byte[] literal = ((InsertLiterDelta) delta).getLiteral();
                System.arraycopy(literal, 0, result, offset, literal.length);
            }
            offset = delta.getTargetRange().getEnd();
        }
        return result;
    }

    public static String pretty(List<Delta> deltas, byte[] base) {
        StringBuilder sb = new StringBuilder();
        for (Delta delta : deltas) {
            if (delta instanceof CopyRangeDelta) {
                sb.append("[copy]{");
                sb.append(((CopyRangeDelta) delta).getOriginRange().readToString(base));
                sb.append("}\n");
            }
            if (delta instanceof InsertLiterDelta) {
                sb.append("[insert]{");
                sb.append(new String(((InsertLiterDelta) delta).getLiteral()));
                sb.append("}\n");
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        //        byte[] target = "abcdefghigklmnopqrstuvwxyz789defghigklmiidfad".getBytes(StandardCharsets.UTF_8);
//        byte[] base = "e34abcdefghigkl123mnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
//        byte[] target = "广域网（英语：Wide Area Network，缩写为 WAN），又dfasdfag打法称广域ssccdggeaswe网、外网、公网。是连接不同地区局域网或wosidfjao城域网计算机通信的远程网。通常跨接很大的物我们都是理范围，所覆盖的里，它能连接多个地区、城市和国家，或横跨几个洲并能提供远距离通信，形成国际性的远程网络。广域网并".getBytes(StandardCharsets.UTF_8);
//        byte[] base = "广域网（英语：Wide Area Network，缩写为 WAN），又称广域网、外网、公网。是连接不同地区局域网或城域网计算机通信的远程网。通常跨接很大的物理范围，所覆盖的范围从几十公里到几千公里，它能连接多个地区、城市和国家，或横跨几个洲并能提供远距离通信，形成国际性的远程网络。广域网并".getBytes(StandardCharsets.UTF_8);
        byte[] target = ("~运算符是对运算数的每一位按位取反。\n" +
                "下表列出了位运算符的基本运算,假设整数变量A的值为60和变量B的值为13：\n" +
                "\n" +
                "\n" +
                "\n" +
                "操作符\n" +
                "描述\n" +
                "例子\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "&\n" +
                "如果相对应位都是1，则结果为1，否则为0\n" +
                "A & B得到 12，即0000 1100\n" +
                "\n" +
                "\n" +
                "|\n" +
                "如果相对应位都是0，则结果为0，否则为1\n" +
                "A | B得到61adsf我们发达佛教排毒分ijdfpjq的你的积分打飞机扩大 计分卡满，即 0011 1101\n" +
                "\n" +
                "\n" +
                "^\n" +
                "如果相对应位值相同，则结果为0，否则为1\n" +
                "A ^ B得到 49，即 0011 0001\n" +
                "\n" +
                "\n1\n" +
                "\n" +
                "\n" +
                "<<\n" +
                "按位左移运算符。左操作数按位左移右操作数指定的位数。（低位补零）\n" +
                "A << 2得到 240，即 1111 0000\n" +
                "\n" +
                "\n" +
                ">>\n" +
                "“有符号”按位右移运算符。打法多个按打法的。该操作符使用 “符号扩展”：若符号为正，则高位插入 0；若符号为负，则高位插入 1。\n" +
                "A >> 2得到15即 1111\n" +
                "\n" +
                "\n" +
                ">>>\n" +
                "“无符号”按位右移补零操作符。左操速度发达发达爱吃地方额外而非作数的值按右操作数指定的位数右移，移动得到的空位以零填充。该操作符使用 “零扩展”，无论正负，都在高位插入 0。\n" +
                "A >>> 2得到 15，即 0000 1111\n" +
                "\n" +
                "\n" +
                "\n" +
                "按位操作符\n" +
                "\n" +
                "特别注意：使用按位操作符时要注意，相\n" +
                "\n" +
                "著作权归作者所有。\n" +
                "商业转载请联系作者获得授权，非商业转载请注明出处。\n" +
                "作者：Nemo\n" +
                "链接：https://www.cnblogs.com/blknemo/ \n" +
                "来源：博短短的客园额哦哦v\n" +
                "\n").getBytes(StandardCharsets.UTF_8);
        byte[] base = ("~运算符是对运算数的每一位按位取反。\n" +
                "下表列出了位运算符的基本运算,假设整数变量A的值为60和变量B的值为13：\n" +
                "\n" +
                "\n" +
                "\n" +
                "操作符\n" +
                "描述\n" +
                "例子\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "&\n" +
                "如果相对应位都是1，则结果为1，否则为0\n" +
                "A & B得到 12，即0000 1100\n" +
                "\n" +
                "\n" +
                "|\n" +
                "如果相对应位都是0，则结果为0，否则为1\n" +
                "A | B得到61，即 0011 1101\n" +
                "\n" +
                "\n" +
                "^\n" +
                "如果相对应位值相同，则结果为0，否则为1\n" +
                "A ^ B得到 49，即 0011 0001\n" +
                "\n" +
                "\n" +
                "~\n" +
                "按位取反运算符翻转操作数的每一位，即0变成1，1变成0。\n" +
                "~A得到 -61，即1100 0011\n" +
                "\n" +
                "\n" +
                "<<\n" +
                "按位左移运算符。左操作数按位左移右操作数指定的位数。（低位补零）\n" +
                "A << 2得到 240，即 1111 0000\n" +
                "\n" +
                "\n" +
                ">>\n" +
                "“有符号”按位右移运算符。左操作数按位右移右操作数指定的位数。该操作符使用 “符号扩展”：若符号为正，则高位插入 0；若符号为负，则高位插入 1。\n" +
                "A >> 2得到15即 1111\n" +
                "\n" +
                "\n" +
                ">>>\n" +
                "“无符号”按位右移补零操作符。左操作数的值按右操作数指定的位数右移，移动得到的空位以零填充。该操作符使用 “零扩展”，无论正负，都在高位插入 0。\n" +
                "A >>> 2得到 15，即 0000 1111\n" +
                "\n" +
                "\n" +
                "\n" +
                "按位操作符\n" +
                "\n" +
                "特别注意：使用按位操作符时要注意，相\n" +
                "\n" +
                "著作权归作者所有。\n" +
                "商业转载请联系作者获得授权，非商业转载请注明出处。\n" +
                "作者：Nemo\n" +
                "链接：https://www.cnblogs.com/blknemo/ \n" +
                "来源：博客园\n" +
                "\n").getBytes(StandardCharsets.UTF_8);

        List<Delta> deltas = makeDeltas(target, base);
        Formatter formatter = Formatter.newInstance();
        byte[] deltasFormatted = formatter.format(deltas);
        List<Delta> parsedDeltas = formatter.parse(deltasFormatted);
        byte[] targetApplied = applyDeltas(deltas, base);


        System.out.println(pretty(parsedDeltas,base).equals(pretty(deltas,base)));
        System.out.println(Arrays.equals(targetApplied, target));
    }
}
