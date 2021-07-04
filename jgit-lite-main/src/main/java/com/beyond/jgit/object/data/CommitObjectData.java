package com.beyond.jgit.object.data;

import com.beyond.jgit.util.BytesUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static java.nio.charset.StandardCharsets.UTF_8;

@Data
public class CommitObjectData implements ObjectData {
    private String tree;
    private List<String> parents = new ArrayList<>();
    private User author;
    private User committer;
    private String message;
    private long commitTime;
    private TimeZone timeZone = TimeZone.getDefault();
    private byte[] signature;

    public void addParent(String parent) {
        parents.add(parent);
    }

    public static CommitObjectData parseFrom(byte[] bytes) {
        CommitObjectData commitObjectData = new CommitObjectData();

        int index = 0;
        byte[] treeObjectId = BytesUtils.collectByLength(bytes, 5, 40);
        index = 46;
        commitObjectData.setTree(new String(treeObjectId));

        while (true) {
            byte[] hparent = BytesUtils.collectByLength(bytes, index, 7);
            if ("parent ".equals(new String(hparent))) {
                index += 7;
                byte[] parentObjectId = BytesUtils.collectByLength(bytes, index, 40);
                commitObjectData.getParents().add(new String(parentObjectId));
                index += parentObjectId.length;
                index += 1;
            } else {
                break;
            }
        }


        byte[] authorBytes = BytesUtils.collectUntil(bytes, index, (byte) '\n');
        UserExt author = UserExt.parseFrom(authorBytes);
        commitObjectData.setAuthor(author);
        index += authorBytes.length + 1;

        byte[] committerBytes = BytesUtils.collectUntil(bytes, index, (byte) '\n');
        UserExt committer = UserExt.parseFrom(committerBytes);
        commitObjectData.setCommitter(committer);
        index += committerBytes.length + 1;

        commitObjectData.setCommitTime(committer.getCommitTime());

        byte[] hgpgsig = BytesUtils.collectByLength(bytes, index, 6);
        if (BytesUtils.equals("gpgsig", hgpgsig)){
            index+=7;
            byte[] signature = BytesUtils.collectUntil(bytes, index, "\n\n".getBytes()); // gpgsig可能为多行
            commitObjectData.setSignature(signature);
            index += signature.length +1;
        }

        index +=1;

        byte[] messageBytes = BytesUtils.collectAfter(bytes, index);
        commitObjectData.setMessage(new String(messageBytes));

        return commitObjectData;
    }

    @Override
    public byte[] toBytes() {
        CommitObjectData commitObjectData = this;
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             OutputStreamWriter w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            os.write("tree".getBytes());
            os.write(' ');
            os.write(commitObjectData.getTree().getBytes());
            os.write('\n');

            for (String parent : commitObjectData.getParents()) {
                os.write("parent".getBytes());
                os.write(' ');
                os.write(parent.getBytes());
                os.write('\n');
            }

            os.write("author".getBytes());
            os.write(' ');
            w.write(getUserExternalString(commitObjectData.getAuthor(), commitObjectData.getCommitTime(), commitObjectData.getTimeZone()));
            w.flush();
            os.write('\n');

            os.write("committer".getBytes());
            os.write(' ');
            w.write(getUserExternalString(commitObjectData.getCommitter(), commitObjectData.getCommitTime(), commitObjectData.getTimeZone()));
            w.flush();
            os.write('\n');

            byte[] signature = this.signature;
            if (signature != null) {
                os.write("gpgsig".getBytes());
                os.write(' ');
                writeMultiLineHeader(new String(signature), os, true);
                os.write('\n');
            }

            Charset encoding = Charset.defaultCharset();
            if (!UTF_8.equals(encoding)) {
                os.write("encoding".getBytes());
                os.write(' ');
                os.write(encoding.name().getBytes());
                os.write('\n');
            }

            os.write('\n');

            if (commitObjectData.getMessage() != null) {
                w.write(commitObjectData.getMessage());
                w.flush();
            }
            return os.toByteArray();
        } catch (IOException err) {
            // This should never occur, the only way to get it above is
            // for the ByteArrayOutputStream to throw, but it doesn't.
            //
            throw new RuntimeException(err);
        }
    }

    static void writeMultiLineHeader(@NonNull String in,
                                     @NonNull OutputStream out, boolean enforceAscii)
            throws IOException, IllegalArgumentException {
        int length = in.length();
        for (int i = 0; i < length; ++i) {
            char ch = in.charAt(i);
            switch (ch) {
                case '\r':
                    if (i + 1 < length && in.charAt(i + 1) == '\n') {
                        ++i;
                    }
                    if (i + 1 < length) {
                        out.write('\n');
                        out.write(' ');
                    }
                    break;
                case '\n':
                    if (i + 1 < length) {
                        out.write('\n');
                        out.write(' ');
                    }
                    break;
                default:
                    // sanity check
                    if (ch > 127 && enforceAscii) {
                        throw new IllegalArgumentException("write commit object fail");
                    }
                    out.write(ch);
                    break;
            }
        }
    }

    public static String getUserExternalString(User user, long commitTime, TimeZone timeZone) {
        final StringBuilder r = new StringBuilder();
        appendSanitized(r, user.getName());
        r.append(" <"); //$NON-NLS-1$
        appendSanitized(r, user.getEmail());
        r.append("> "); //$NON-NLS-1$
        r.append(commitTime / 1000);
        r.append(' ');
        int tzOffset = timeZone.getRawOffset() / 1000 / 60;
        appendTimezone(r, tzOffset);
        return r.toString();
    }

    public static void appendSanitized(StringBuilder r, String str) {
        // Trim any whitespace less than \u0020 as in String#trim().
        int i = 0;
        while (i < str.length() && str.charAt(i) <= ' ') {
            i++;
        }
        int end = str.length();
        while (end > i && str.charAt(end - 1) <= ' ') {
            end--;
        }

        for (; i < end; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\n':
                case '<':
                case '>':
                    continue;
                default:
                    r.append(c);
                    break;
            }
        }
    }


    public static void appendTimezone(StringBuilder r, int offset) {
        final char sign;
        final int offsetHours;
        final int offsetMins;

        if (offset < 0) {
            sign = '-';
            offset = -offset;
        } else {
            sign = '+';
        }

        offsetHours = offset / 60;
        offsetMins = offset % 60;

        r.append(sign);
        if (offsetHours < 10) {
            r.append('0');
        }
        r.append(offsetHours);
        if (offsetMins < 10) {
            r.append('0');
        }
        r.append(offsetMins);
    }


    @Data
    public static class User {
        private String name;
        private String email;

        public static User parseFrom(byte[] bytes){
            String s = new String(bytes);
            String[] s1 = s.split(" ");
            User user = new User();
            user.setName(s1[1]);
            user.setEmail(s1[2].substring(1, s1[2].length() - 1));
            return user;
        }
    }


    @EqualsAndHashCode(callSuper = true)
    @Data
    @ToString(callSuper = true)
    private static class UserExt  extends User{
        private long commitTime;

        public static UserExt parseFrom(byte[] bytes){
            String s = new String(bytes);
            String[] s1 = s.split(" ");
            UserExt user = new UserExt();
            user.setName(s1[1]);
            user.setEmail(s1[2].substring(1, s1[2].length() - 1));
            user.setCommitTime(Integer.parseInt(s1[3]) * 1000L);
            return user;
        }
    }

}
