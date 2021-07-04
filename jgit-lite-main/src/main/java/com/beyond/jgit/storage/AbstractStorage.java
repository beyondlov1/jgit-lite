package com.beyond.jgit.storage;

import java.io.IOException;

public abstract class AbstractStorage implements Storage{
    @Override
    public String readFullToString(String path) throws IOException {
        return new String(readFullyToByteArray(path));
    }
}
