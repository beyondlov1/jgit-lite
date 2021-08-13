package com.beyond.jgit.util;


import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtils {

    public static String concat(String path, String... otherPaths) {
        String result = "";
        URI uri = URI.create(path);
        if (uri.getScheme() != null) {
            result += uri.getScheme();
            result += "://";
        }
        if (uri.getHost() != null) {
            result += uri.getHost();
        }
        if (uri.getPort() != -1) {
            result += ":";
            result += uri.getPort();
        }
        if (uri.getPath() != null) {
            String pathTmp = uri.getPath();
            for (String otherPath : otherPaths) {
                pathTmp = concatInternal(pathTmp, otherPath);
            }
            result += pathTmp;
        }
        return result;
    }

    private static String concatInternal(String path, String otherPath) {
        return Paths.get(path, otherPath).toString();
    }

    public static String parent(String path) {
        StringBuilder sb = new StringBuilder();
        URI uri = URI.create(path);
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme());
            sb.append("://");
        }
        if (uri.getHost() != null) {
            sb.append(uri.getHost());
        }
        if (uri.getPort() != -1) {
            sb.append(":");
            sb.append(uri.getPort());
        }
        if (uri.getPath() != null) {
            if (Paths.get(uri.getPath()).getParent() != null) {
                sb.append(Paths.get(uri.getPath()).getParent());
            }
        }
        return sb.toString();
    }

    public static String root(String path) {
        StringBuilder sb = new StringBuilder();
        URI uri = URI.create(path);
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme());
            sb.append("://");
        }
        if (uri.getHost() != null) {
            sb.append(uri.getHost());
        }
        if (uri.getPort() != -1) {
            sb.append(":");
            sb.append(uri.getPort());
        }
        if (uri.getPath() != null) {
            sb.append(Paths.get(uri.getPath()).getRoot().toString());
        }
        return sb.toString();
    }

    public static String getName(String path) {
        URI uri = URI.create(path);
        Path normalPath = Paths.get(uri.getPath());
        return normalPath.getFileName().toString();
    }

    public static String getRelativePath(String rootPath, String absPath) {
        String s = absPath.replaceFirst(rootPath, "");
        if (s.startsWith(File.separator)) {
            return s.replaceFirst(File.separator, "");
        }
        return s;
    }

    public static boolean equals(String path1, String path2) {
        return Paths.get(path1).equals(Paths.get(path2));
    }

    public static void main(String[] args) {
        Path path = Paths.get("/api/hello/yes/hello.txt");
        Path fileName = path.getFileName();
        Path parent = path.getParent();
        Path root = path.getRoot();
        System.out.println(fileName);
        System.out.println(parent);
        System.out.println(root);

        URI uri = URI.create("http://www.baidu.com/api/heloo/yes/hel.txt#aa");
        System.out.println(uri.getPath());
        System.out.println(uri.getHost());
        System.out.println(uri.getAuthority());
        System.out.println(uri.getFragment());
        System.out.println(uri.getScheme());
        System.out.println(uri.getSchemeSpecificPart());

        String parent1 = parent("https://dav.jianguoyun.com/dav/FILE_CLUSTER/app/hello.txt");
        System.out.println(parent1);

        String concat = concat("", "yesf");
        System.out.println(concat);

        String root1 = root("https://dav.jianguoyun.com/dav/FILE_CLUSTER/app/hello.txt");
        System.out.println(root1);

    }



}
