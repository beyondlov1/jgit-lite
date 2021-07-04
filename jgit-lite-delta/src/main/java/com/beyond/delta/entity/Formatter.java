package com.beyond.delta.entity;

import java.util.List;

/**
 * @author chenshipeng
 * @date 2021/07/02
 */
public interface Formatter {

    static Formatter newInstance(){
        return new FormatterImpl();
    }

    byte[] format(List<Delta> deltas);
    void format(List<Delta> deltas, byte[] result, int offset);
    List<Delta> parse(byte[] deltasBytes);
    List<Delta> parse(byte[] deltasBytes, int offset, int len);
}
