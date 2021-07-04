package com.beyond.jgit.pack;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OfsDeltaBlock extends DeltaBlock{
    private int ofs;
}
