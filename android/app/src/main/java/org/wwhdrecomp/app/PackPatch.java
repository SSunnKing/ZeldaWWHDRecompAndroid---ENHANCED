package org.wwhdrecomp.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Applies a pack patch (tools/translation/make_pack_patch.py, format there) to the game's own
 * message pack (a big-endian SARC of Yaz0 files): patched files are written as stored Yaz0, the
 * others are copied as they are, the archive is laid out again with the same names and order.
 */
final class PackPatch {
    private PackPatch() {}

    private static final class Entry {
        int kind;
        byte[] content;      // kind 0
        int origLen;         // kind 1
        long origCrc;
        int[] offsets;
        byte[][] bytes;
    }

    static void apply(File origFile, byte[] patch, File outFile) throws IOException {
        Map<String, Entry> entries = parse(patch);
        byte[] d = Files.readAllBytes(origFile.toPath());
        ByteBuffer b = ByteBuffer.wrap(d);  // big endian
        if (d.length < 0x20 || b.getInt(0) != 0x53415243 || b.getShort(6) != (short) 0xFEFF) throw new IOException("not a SARC pack");
        int data = b.getInt(12), n = b.getShort(0x1A) & 0xFFFF, names = 0x20 + 16 * n + 8;
        byte[] head = java.util.Arrays.copyOf(d, data);
        ByteBuffer h = ByteBuffer.wrap(head);
        ByteArrayOutputStream body = new ByteArrayOutputStream(d.length + (1 << 20));
        int applied = 0;
        for (int i = 0; i < n; i++) {
            int node = 0x20 + 16 * i, attr = b.getInt(node + 4), s = b.getInt(node + 8), e = b.getInt(node + 12);
            int o = names + (attr & 0xFFFFFF) * 4, z = o;
            while (d[z] != 0) z++;
            String name = new String(d, o, z - o, java.nio.charset.StandardCharsets.US_ASCII);
            byte[] ent = java.util.Arrays.copyOfRange(d, data + s, data + e);
            Entry p = entries.get(name);
            if (p != null) {
                byte[] c;
                if (p.kind == 0) {
                    c = p.content;
                } else {
                    c = yaz0(ent);
                    CRC32 crc = new CRC32();
                    crc.update(c);
                    if (c.length != p.origLen || crc.getValue() != p.origCrc)
                        throw new IOException(name + " differs from the version the translation was made for");
                    for (int r = 0; r < p.offsets.length; r++) System.arraycopy(p.bytes[r], 0, c, p.offsets[r], p.bytes[r].length);
                }
                ent = storedYaz0(c, ent);
                applied++;
            }
            while (body.size() % 0x100 != 0) body.write(0);
            int start = body.size();
            body.write(ent);
            h.putInt(node + 8, start);
            h.putInt(node + 12, body.size());
        }
        if (applied != entries.size()) throw new IOException("the pack lacks files the translation changes");
        h.putInt(8, head.length + body.size());
        File tmp = new File(outFile.getPath() + ".part");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(head);
            body.writeTo(out);
        }
        if (!tmp.renameTo(outFile)) throw new IOException("cannot write " + outFile);
    }

    private static Map<String, Entry> parse(byte[] p) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(p);
        if (p.length < 12 || b.getInt() != 0x57575054 || b.getInt() != 1) throw new IOException("bad translation patch");
        int count = b.getInt();
        Map<String, Entry> m = new HashMap<>();
        for (int i = 0; i < count; i++) {
            byte[] nm = new byte[b.getShort() & 0xFFFF];
            b.get(nm);
            Entry e = new Entry();
            e.kind = b.get();
            if (e.kind == 0) {
                e.content = new byte[b.getInt()];
                b.get(e.content);
            } else {
                e.origLen = b.getInt();
                e.origCrc = b.getInt() & 0xFFFFFFFFL;
                int nr = b.getInt();
                e.offsets = new int[nr];
                e.bytes = new byte[nr][];
                for (int r = 0; r < nr; r++) {
                    e.offsets[r] = b.getInt();
                    e.bytes[r] = new byte[b.getInt()];
                    b.get(e.bytes[r]);
                }
            }
            m.put(new String(nm, java.nio.charset.StandardCharsets.US_ASCII), e);
        }
        return m;
    }

    static byte[] yaz0(byte[] d) throws IOException {
        if (d.length < 16 || d[0] != 'Y' || d[1] != 'a' || d[2] != 'z' || d[3] != '0') return d;
        int n = ByteBuffer.wrap(d).getInt(4);
        byte[] out = new byte[n];
        int p = 16, q = 0;
        try {
            while (q < n) {
                int f = d[p++] & 0xFF;
                for (int i = 0; i < 8 && q < n; i++) {
                    if ((f & (0x80 >> i)) != 0) {
                        out[q++] = d[p++];
                    } else {
                        int b1 = d[p++] & 0xFF, b2 = d[p++] & 0xFF;
                        int dist = ((b1 & 0xF) << 8 | b2) + 1, c = b1 >> 4;
                        c = c == 0 ? (d[p++] & 0xFF) + 0x12 : c + 2;
                        for (int k = 0; k < c && q < n; k++, q++) out[q] = out[q - dist];
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("corrupt Yaz0 data");
        }
        return out;
    }

    // Yaz0 with every byte stored literally; the header's alignment field kept from the original
    private static byte[] storedYaz0(byte[] c, byte[] orig) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(c.length + c.length / 8 + 32);
        o.write('Y'); o.write('a'); o.write('z'); o.write('0');
        o.write(c.length >>> 24); o.write(c.length >>> 16); o.write(c.length >>> 8); o.write(c.length);
        o.write(orig, 8, 8);
        for (int i = 0; i < c.length; i += 8) {
            int len = Math.min(8, c.length - i);
            o.write((0xFF << (8 - len)) & 0xFF);
            o.write(c, i, len);
        }
        return o.toByteArray();
    }
}
