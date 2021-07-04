package com.beyond.delta.entity;

import lombok.Data;

@Data
public class OriginChunk {
    private int index;
    private Range range;
    private String hash;
}
