package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.Data;

import java.util.List;

@Data
public abstract class DeltaBlock extends Block {
    private List<Delta> deltas;

    public DeltaBlock() {
    }

    public DeltaBlock(String objectId, List<Delta> deltas) {
        setObjectId(objectId);
        this.deltas = deltas;
    }
}
