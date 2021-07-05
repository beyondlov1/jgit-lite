package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class RefDeltaBlock extends DeltaBlock {
    private String ref;

    public RefDeltaBlock() {
    }

    public RefDeltaBlock(List<Delta> deltas) {
        super(deltas);
    }
}
