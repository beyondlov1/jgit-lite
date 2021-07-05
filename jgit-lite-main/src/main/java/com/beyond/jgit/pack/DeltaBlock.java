package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.Data;

import java.util.List;

@Data
public abstract class DeltaBlock {
    private int start;
    private int end;
    private List<Delta> deltas;
}
