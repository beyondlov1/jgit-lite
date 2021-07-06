package com.beyond.jgit.pack;

import com.beyond.delta.entity.Delta;
import lombok.*;

import java.util.List;


@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class OfsDeltaBlock extends DeltaBlock{
    private int ofs;

    public OfsDeltaBlock() {
    }

    public OfsDeltaBlock(String objectId, List<Delta> deltas) {
        super(objectId, deltas);
    }
}
