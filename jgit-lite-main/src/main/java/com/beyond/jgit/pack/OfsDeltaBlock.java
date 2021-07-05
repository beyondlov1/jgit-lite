package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class OfsDeltaBlock extends DeltaBlock{
    private int ofs;

    public OfsDeltaBlock() {
    }

    public OfsDeltaBlock(List<Delta> deltas) {
        super(deltas);
    }
}
