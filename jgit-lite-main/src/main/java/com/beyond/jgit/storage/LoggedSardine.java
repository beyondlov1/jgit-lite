package com.beyond.jgit.storage;

import com.thegrizzlylabs.sardineandroid.*;
import com.thegrizzlylabs.sardineandroid.report.SardineReport;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class LoggedSardine implements Sardine {
    private final Sardine sardine;

    public LoggedSardine(Sardine sardine) {
        this.sardine = sardine;
    }

    @Override
    public void setCredentials(String s, String s1, boolean b) {
        sardine.setCredentials(s, s1, b);
    }

    @Override
    public void setCredentials(String s, String s1) {
        sardine.setCredentials(s, s1);
    }

    @Override
    @Deprecated
    public List<DavResource> getResources(String s) throws IOException {
        return sardine.getResources(s);
    }

    @Override
    public List<DavResource> list(String s) throws IOException {
        log.debug("list:"+s);
        return sardine.list(s);
    }

    @Override
    public List<DavResource> list(String s, int i) throws IOException {
        log.debug("list:"+s);
        return sardine.list(s, i);
    }

    @Override
    public List<DavResource> list(String s, int i, Set<QName> set) throws IOException {
        log.debug("list:"+s);

        return sardine.list(s, i, set);
    }

    @Override
    public List<DavResource> list(String s, int i, boolean b) throws IOException {
        log.debug("list:"+s);

        return sardine.list(s, i, b);
    }

    @Override
    public List<DavResource> propfind(String s, int i, Set<QName> set) throws IOException {
        return sardine.propfind(s, i, set);
    }

    @Override
    public <T> T report(String s, int i, SardineReport<T> sardineReport) throws IOException {
        return sardine.report(s, i, sardineReport);
    }

    @Override
    public List<DavResource> search(String s, String s1, String s2) throws IOException {
        return sardine.search(s, s1, s2);
    }

    @Override
    @Deprecated
    public void setCustomProps(String s, Map<String, String> map, List<String> list) throws IOException {
        sardine.setCustomProps(s, map, list);
    }

    @Override
    public List<DavResource> patch(String s, Map<QName, String> map) throws IOException {
        return sardine.patch(s, map);
    }

    @Override
    public List<DavResource> patch(String s, Map<QName, String> map, List<QName> list) throws IOException {
        return sardine.patch(s, map, list);
    }

    @Override
    public List<DavResource> patch(String s, List<Element> list, List<QName> list1) throws IOException {
        return sardine.patch(s, list, list1);
    }

    @Override
    public InputStream get(String s) throws IOException {
        log.debug("get:"+s);

        return sardine.get(s);
    }

    @Override
    public InputStream get(String s, Map<String, String> map) throws IOException {
        log.debug("get:"+s);

        return sardine.get(s, map);
    }

    @Override
    public void put(String s, byte[] bytes) throws IOException {
        log.debug("put:"+s);

        sardine.put(s, bytes);
    }

    @Override
    public void put(String s, byte[] bytes, String s1) throws IOException {
        log.debug("put:"+s);

        sardine.put(s, bytes, s1);
    }

    @Override
    public void put(String s, File file, String s1) throws IOException {
        log.debug("put:"+s);

        sardine.put(s, file, s1);
    }

    @Override
    public void put(String s, File file, String s1, boolean b) throws IOException {
        log.debug("put:"+s);

        sardine.put(s, file, s1, b);
    }

    @Override
    public void put(String s, File file, String s1, boolean b, String s2) throws IOException {
        log.debug("put:"+s);

        sardine.put(s, file, s1, b, s2);
    }

    @Override
    public void delete(String s) throws IOException {
        log.debug("delete:"+s);

        sardine.delete(s);
    }

    @Override
    public void createDirectory(String s) throws IOException {
        log.debug("createDirectory:"+s);

        sardine.createDirectory(s);
    }

    @Override
    public void move(String s, String s1) throws IOException {
        sardine.move(s, s1);
    }

    @Override
    public void move(String s, String s1, boolean b) throws IOException {
        sardine.move(s, s1, b);
    }

    @Override
    public void move(String s, String s1, boolean b, String s2) throws IOException {
        sardine.move(s, s1, b, s2);
    }

    @Override
    public void copy(String s, String s1) throws IOException {
        sardine.copy(s, s1);
    }

    @Override
    public void copy(String s, String s1, boolean b) throws IOException {
        sardine.copy(s, s1, b);
    }

    @Override
    public boolean exists(String s) throws IOException {
        log.debug("exists:"+s);

        return sardine.exists(s);
    }

    @Override
    public String lock(String s) throws IOException {
        log.debug("lock:"+s);

        return sardine.lock(s);
    }

    @Override
    public String lock(String s, int i) throws IOException {
        log.debug("lock:"+s);

        return sardine.lock(s, i);
    }

    @Override
    public String refreshLock(String s, String s1, String s2) throws IOException {
        log.debug("refreshLock:"+s);

        return sardine.refreshLock(s, s1, s2);
    }

    @Override
    public void unlock(String s, String s1) throws IOException {
        log.debug("unlock:"+s);
        sardine.unlock(s, s1);
    }

    @Override
    public DavAcl getAcl(String s) throws IOException {
        return sardine.getAcl(s);
    }

    @Override
    public DavQuota getQuota(String s) throws IOException {
        return sardine.getQuota(s);
    }

    @Override
    public void setAcl(String s, List<DavAce> list) throws IOException {
        sardine.setAcl(s, list);
    }

    @Override
    public List<DavPrincipal> getPrincipals(String s) throws IOException {
        return sardine.getPrincipals(s);
    }

    @Override
    public List<String> getPrincipalCollectionSet(String s) throws IOException {
        return sardine.getPrincipalCollectionSet(s);
    }

    @Override
    public void enableCompression() {
        sardine.enableCompression();
    }

    @Override
    public void disableCompression() {
        sardine.disableCompression();
    }

    @Override
    public void ignoreCookies() {
        sardine.ignoreCookies();
    }

    @Override
    public void close() {
        sardine.close();
    }
}
