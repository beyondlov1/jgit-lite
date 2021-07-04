package com.beyond.delta.entity;

import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class Range {
    private int start;
    private int end;

    public Range(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public byte[] read(byte[] bytes){
        byte[] result = new byte[end - start];
        System.arraycopy(bytes, start, result,0, end - start);
        return result;
    }


    public String readToString(byte[] bytes){
        return new String(read(bytes), StandardCharsets.UTF_8);
    }

    public int length(){
        return end - start;
    }
}
