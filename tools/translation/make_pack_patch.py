#!/usr/bin/env python3
"""Builds a pack patch: the difference between the game's own message pack and a translated one.

    make_pack_patch.py <original permanent_2d_UsEnglish.pack> <translated pack> <out patch.bin>

The app (Translation.java) rebuilds the translated pack from the player's own game files and this
patch, so the patch carries only what the translation changes: whole files for the message texts
(.msbt) and the message project (.msbp), byte ranges for the fonts. Files the translation only
recompressed (equal once decompressed) are left out. Nothing of the game itself is in the patch.

Format (big endian): "WWPT", u32 version 1, u32 entries; per entry: u16 name length, name,
u8 kind; kind 0 (new content): u32 length, bytes; kind 1 (byte ranges over the original content):
u32 original length, u32 original CRC-32, u32 ranges, per range u32 offset, u32 length, bytes.
Contents are decompressed (the app writes them as stored Yaz0).
"""
import struct
import sys
import zlib


def sarc(path):
    d = open(path, 'rb').read()
    if d[:4] != b'SARC' or d[6:8] != b'\xfe\xff':
        sys.exit(f'{path}: not a big-endian SARC')
    data = struct.unpack('>I', d[12:16])[0]
    n = struct.unpack('>H', d[0x1A:0x1C])[0]
    nodes = [struct.unpack('>IIII', d[0x20 + 16 * i:0x30 + 16 * i]) for i in range(n)]
    names = 0x20 + 16 * n + 8
    out = {}
    for _, attr, s, e in nodes:
        o = names + (attr & 0xFFFFFF) * 4
        out[d[o:d.index(b'\0', o)].decode()] = d[data + s:data + e]
    return out


def yaz0(d):
    if d[:4] != b'Yaz0':
        return d
    n = int.from_bytes(d[4:8], 'big')
    out = bytearray()
    p = 16
    while len(out) < n:
        f = d[p]
        p += 1
        for i in range(8):
            if len(out) >= n:
                break
            if f & (0x80 >> i):
                out.append(d[p])
                p += 1
            else:
                b1, b2 = d[p], d[p + 1]
                p += 2
                dist = ((b1 & 0xF) << 8 | b2) + 1
                c = b1 >> 4
                if c == 0:
                    c = d[p] + 0x12
                    p += 1
                else:
                    c += 2
                for _ in range(c):
                    out.append(out[-dist])
    return bytes(out)


def ranges(a, b, gap=64):
    out, i, n = [], 0, len(a)
    while i < n:
        if a[i] == b[i]:
            i += 1
            continue
        j = i
        while j < n:  # extend over short equal runs
            if a[j] != b[j]:
                j += 1
                continue
            k = j
            while k < n and k - j < gap and a[k] == b[k]:
                k += 1
            if k - j >= gap or k == n:
                break
            j = k
        out.append((i, b[i:j]))
        i = j
    return out


def main():
    orig, tr, outp = sarc(sys.argv[1]), sarc(sys.argv[2]), sys.argv[3]
    if sorted(orig) != sorted(tr):
        sys.exit('the packs hold different files')
    entries = []
    for name in sorted(tr):
        a, b = yaz0(orig[name]), yaz0(tr[name])
        if a == b:
            continue
        e = struct.pack('>H', len(name.encode())) + name.encode()
        if len(a) == len(b) and name.endswith('_bffnt.szs'):
            r = ranges(a, b)
            e += struct.pack('>BIII', 1, len(a), zlib.crc32(a), len(r))
            for off, bs in r:
                e += struct.pack('>II', off, len(bs)) + bs
        else:
            e += struct.pack('>BI', 0, len(b)) + b
        entries.append(e)
    with open(outp, 'wb') as f:
        f.write(b'WWPT' + struct.pack('>II', 1, len(entries)) + b''.join(entries))
    print(f'{len(entries)} files changed, patch {sum(map(len, entries))} bytes')


if __name__ == '__main__':
    main()
