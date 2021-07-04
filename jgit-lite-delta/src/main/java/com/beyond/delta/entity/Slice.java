package com.beyond.delta.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Slice {
    private Slice pre;
    private Slice next;
    private int start;
    private int end;
    private List<Candidate> candidates = new ArrayList<>();
    private Candidate bestCandidate;
    private boolean interrupted;

    @Override
    public String toString() {
        return "Slice{" +
                "start=" + start +
                ", candidates=" + candidates +
                ", bestCandidate=" + bestCandidate +
                '}';
    }

    @Data
    public static class Candidate {
        private Candidate pre;
        private Candidate next;
        private OriginChunk originChunk;
        private int totalLength;
        private Slice targetSlice;

        @Deprecated
        public Range mergeOriginRange() {
            if (pre == null) {
                return originChunk.getRange();
            }
            return new Range(pre.mergeOriginRange().getStart(), originChunk.getRange().getEnd());
        }


        /**
         * 会出现范围交叉问题，改用 mergeTargetRange
         */
        @Deprecated
        public Range mergeTargetRange() {
            if (pre == null) {
                return new Range(this.targetSlice.getStart(), this.targetSlice.getEnd());
            }
            return new Range(pre.mergeTargetRange().getStart(), this.targetSlice.getEnd());
        }

        /**
         * 防止与上一个出现交叉
         */
        public Range mergeTargetRange(int until) {
            if (pre == null) {
                return new Range(this.targetSlice.getStart(), this.targetSlice.getEnd());
            }
            if (pre.getTargetSlice().getStart() < until) {
                return new Range(this.targetSlice.getStart(), this.targetSlice.getEnd());
            }
            return new Range(pre.mergeTargetRange(until).getStart(), this.targetSlice.getEnd());
        }

        @Override
        public String toString() {
            return "Candidate{" +
                    "originChunk=" + originChunk +
                    ", totalLength=" + totalLength +
                    '}';
        }
    }
}
