package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class RefDeltaBlock extends DeltaBlock {
    private String ref;

    public RefDeltaBlock() {
    }

    public RefDeltaBlock(String objectId, List<Delta> deltas) {
        super(objectId, deltas);
    }
}
