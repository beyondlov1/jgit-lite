package com.beyond.jgit.pack;

import lombok.Data;

@Data
public abstract class Block {
    private String objectId;
    private int start;
    private int end;
}
