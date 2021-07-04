package com.beyond.jgit.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.util.*;

public class FileUtil {

    public static Collection<File> listFilesAndDirsWithoutNameOf(String rootPath,String... excludeNames){
        Set<String> excludeNameSet = new HashSet<>(Arrays.asList(excludeNames));
        return FileUtils.listFilesAndDirs(new File(rootPath), TrueFileFilter.INSTANCE, new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                return !excludeNameSet.contains(file.getName());
            }

            @Override
            public boolean accept(File dir, String name) {
                return false;
            }
        });
    }

    public static Collection<File> listChildFilesAndDirsWithoutNameOf(String rootPath,String... excludeNames){
        Set<String> excludeNameSet = new HashSet<>(Arrays.asList(excludeNames));
        File rootFile = new File(rootPath);
        Collection<File> files = FileUtils.listFilesAndDirs(rootFile, TrueFileFilter.INSTANCE, new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                return !excludeNameSet.contains(file.getName());
            }

            @Override
            public boolean accept(File dir, String name) {
                return false;
            }
        });
        files.remove(rootFile);
        return files;
    }


    public static Collection<File> listChildFilesWithoutDirOf(String rootPath,String... excludeNames){
        Set<String> excludeNameSet = new HashSet<>(Arrays.asList(excludeNames));
        File rootFile = new File(rootPath);
        File[] files = rootFile.listFiles((dir, name) -> !excludeNameSet.contains(name));
        if (files == null){
            return Collections.emptyList();
        }
        return Arrays.asList(files);
    }

    public static  Collection<File> listChildOnlyFilesWithoutDirOf(String rootPath,String... excludeNames) {
        Collection<File> files = listFilesAndDirsWithoutNameOf(rootPath, excludeNames);
        Set<File> newSet = new HashSet<>();
        for (File file : files) {
            if (file.isFile()){
                newSet.add(file);
            }
        }
        return newSet;
    }


    public static void main(String[] args) {
        Collection<File> files = listChildFilesWithoutDirOf("/home/beyond/Documents/tmp-git-2-local", ".git");
        for (File file : files) {
            System.out.println(file.getPath());
        }
    }

}
