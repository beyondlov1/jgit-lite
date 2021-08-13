package com.beyond.jgit.util;

import com.sun.org.apache.xpath.internal.operations.Or;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * @author chenshipeng
 * @date 2021/08/13
 */
public class DiffTest {
    public static void main(String[] args) {
        DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
        String base = "\n" +
                "html,\n" +
                "body {\n" +
                "  width: 100%;\n" +
                "  height: 100%;\n" +
                "  margin: 0;\n" +
                "  padding: 0;\n" +
                "  overflow: hidden;\n" +
                "  font-family: 'Fira Mono', helvetica, arial, sans-serif;\n" +
                "  font-weight: 400;\n" +
                "  font-size: 62.5%;\n" +
                "}\n" +
                "\n" +
                "#webgl-container {\n" +
                "  width: 100%;\n" +
                "  height: 100%;";
        LinkedList<DiffMatchPatch.Patch> patches = diffMatchPatch.patch_make(base,

                "/*以下为演示内容，请添加您自己的内容 ^_^ */\n" +
                "\n" +
                "html,\n" +
                "body {\n" +
                "  width: 100%;\n" +
                "  height: 100%;\n" +
                "  margin: 0;\n" +
                "  padding: 1;\n" +
                "  overflow: hiddean;\n" +
                "  font-family: 'Fira Mono', helvetica, arial, sans-serif;\n" +
                "  font-weight: 400;\n" +
                "  font-size: 62.0%;\n" +
                "}\n" +
                "\n" +
                "#webgl-container {\n" +
                "  width: 100%;\n" +
                "  height: 100%;\n" +
                "  cursor: pointer;\n" +
                "}");


        for (DiffMatchPatch.Patch patch : patches) {
            System.out.println(patch);
        }
        Object[] objects = diffMatchPatch.patch_apply(patches, base);
        System.out.println(Arrays.toString(objects));
    }

}
