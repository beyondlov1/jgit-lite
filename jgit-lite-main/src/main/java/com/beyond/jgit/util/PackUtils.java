package com.beyond.jgit.util;

import org.apache.commons.lang3.StringUtils;

public class PackUtils {
    public static String getIndexPath(String packPath){
        String basePath = StringUtils.substringBefore(packPath, ".pack");
        return basePath + ".idx";
    }
}
