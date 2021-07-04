package com.beyond.jgit.diff;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ObjectDiffResult {
    private List<String> leftExtraObjectIds = new ArrayList<>();
    private List<String> rightExtraObjectIds = new ArrayList<>();
}
