package com.beyond.jgit.util;

public class GoogleDiffUtils {
    public static boolean isCross(DiffMatchPatch.Patch patch1, DiffMatchPatch.Patch patch2){
        if (patch1.start1 <= patch2.start1 + patch2.length1 && patch1.start1 >= patch2.start1) {
            return true;
        }

        return patch2.start1 <= patch1.start1 + patch1.length1 && patch2.start1 >= patch1.start1;
    }
}
