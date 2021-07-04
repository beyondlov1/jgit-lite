package com.beyond.delta.entity;

import lombok.Data;

import java.util.List;

@Data
public abstract class DeltaBlock {
    private int offset;
    private List<Delta> deltas;
}
