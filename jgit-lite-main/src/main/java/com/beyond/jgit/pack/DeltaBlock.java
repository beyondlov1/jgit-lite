package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.Data;

import java.util.List;

@Data
public abstract class DeltaBlock {
    private int start;
    private int end;
    private String objectId;
    private List<Delta> deltas;

    public DeltaBlock() {
    }


    public DeltaBlock(String objectId, List<Delta> deltas) {
        this.objectId = objectId;
        this.deltas = deltas;
    }
}
