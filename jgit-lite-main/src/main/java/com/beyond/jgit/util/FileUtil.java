package com.beyond.jgit.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FileUtil {

    public static void move(File source, File target) throws IOException {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static void move(String source, String target) throws IOException {
        Files.move(new File(source).toPath(), new File(target).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

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

    public static Collection<File> listFilesAndDirs(String rootPath, Predicate<File> filter){
        return FileUtils.listFilesAndDirs(new File(rootPath), TrueFileFilter.INSTANCE, new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                return filter.test(file);
            }

            @Override
            public boolean accept(File dir, String name) {
                return false;
            }
        });
    }

    public static Collection<File> listChildFilesAndDirs(String rootPath, Predicate<File> filter){
        return FileUtils.listFilesAndDirs(new File(rootPath), TrueFileFilter.INSTANCE, new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                return filter.test(file);
            }

            @Override
            public boolean accept(File dir, String name) {
                return false;
            }
        }).stream().filter(x -> !new File(rootPath).equals(x)).collect(Collectors.toList());
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


    @Deprecated
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

    public static  Collection<File> listChildOnlyFilesWithoutDirOf(String rootPath, Predicate<File> filter,String... excludeNames) {
        Collection<File> files = listFilesAndDirsWithoutNameOf(rootPath,excludeNames);
        Set<File> newSet = new HashSet<>();
        for (File file : files) {
            if (file.isFile()){
                if (filter.test(file)){
                    newSet.add(file);
                }
            }
        }
        return newSet;
    }



    public static void main(String[] args) {
        Collection<File> files = listChildFilesAndDirs("/home/beyond/Documents/tmp-git-2", x->{
            if (x.getName().startsWith(".")){
                return false;
            }
            return true;
        });
        for (File file : files) {
            System.out.println(file.getPath());
        }
    }

}
