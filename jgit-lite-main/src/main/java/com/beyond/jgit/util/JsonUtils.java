package com.beyond.jgit.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> String writeValueAsString(T t) {
        try {
            return objectMapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            log.error("encode error", e);
            return null;
        }
    }

    public static <T> byte[] writeValueAsBytes(T t) {
        try {
            return objectMapper.writeValueAsBytes(t);
        } catch (JsonProcessingException e) {
            log.error("encode error", e);
            return null;
        }
    }

    public static <T> T readValue(String s, Class<T> tClass) {
        if (StringUtils.isBlank(s)){
            return null;
        }
        try {
            return objectMapper.readValue(s, tClass);
        } catch (IOException e) {
            log.error("decode error", e);
            return null;
        }
    }


    public static <T> T readValue(File file, Class<T> tClass) throws IOException {
        if (!file.exists()){
            return null;
        }
        String s = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        if (StringUtils.isBlank(s)){
            return null;
        }
        try {
            return objectMapper.readValue(s, tClass);
        } catch (IOException e) {
            log.error("decode error", e);
            return null;
        }
    }


    public static <T> T readValue(String s, TypeReference<T> typeReference) {
        if (StringUtils.isBlank(s)){
            return null;
        }
        try {
            return objectMapper.readValue(s, typeReference);
        } catch (IOException e) {
            log.error("decode error", e);
            return null;
        }
    }

    public static <T> T readValue(byte[] bytes, Class<T> tClass) {
        if (ArrayUtils.isEmpty(bytes)){
            return null;
        }
        try {
            return objectMapper.readValue(bytes, tClass);
        } catch (IOException e) {
            log.error("decode error", e);
            return null;
        }
    }


}
