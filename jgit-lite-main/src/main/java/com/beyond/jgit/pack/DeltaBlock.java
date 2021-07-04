package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.Data;

import java.util.List;

@Data
public abstract class DeltaBlock {
    private int offset;
    private List<Delta> deltas;
}
