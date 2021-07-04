package com.beyond.delta.entity;

import lombok.Data;

import java.util.Arrays;

@Data
public class InsertLiterDelta implements Delta {

    private Range targetRange;
    private byte[] literal;

    public InsertLiterDelta(Range targetRange, byte[] literal) {
        this.targetRange = targetRange;
        this.literal = literal;
    }

    @Override
    public String toString() {
        return "InsertLiterDelta{" +
                "targetRange=" + targetRange +
                ", literal=" + new String(literal) +
                '}';
    }
}
