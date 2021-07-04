package com.beyond.delta.entity;

import lombok.Data;

@Data
public class CopyRangeDelta implements Delta {

    private Range originRange;
    private Range targetRange;

    public CopyRangeDelta(Range originRange, Range targetRange) {
        this.originRange = originRange;
        this.targetRange = targetRange;
    }
}
