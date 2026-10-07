"""
Independent Python transliteration of GlucoseDirect's Libre 2 EU code (SensorUtility.swift,
FactoryCalibration.swift; MIT, (c) 2023 Reimar Metzen) used ONLY to generate golden vectors
for app/src/test/.../Libre2Test.kt.

Why a second implementation: Python ints are unbounded and bytes are unsigned, so this copy
can't share the Kotlin port's likely bugs (signed Bytes, missing 16-bit masks). If both agree
on random inputs, the Kotlin port matches the Swift semantics.

Usage:  python tools/libre2_reference.py <path to GlucoseDirect FactoryCalibration.swift>
Prints Kotlin constants to paste into Libre2Test.kt.
"""
import math
import random
import re
import sys

KEYS = [0xA0C5, 0x6860, 0x0000, 0x14C6]
M16 = 0xFFFF


def w(high, low):  # Swift UInt16(high, low)
    return ((high & 0xFF) << 8) + (low & 0xFF)


def op(v):
    res = v >> 2
    if v & 1:
        res ^= KEYS[1]
    if v & 2:
        res ^= KEYS[0]
    return res & M16


def process_crypto(i):
    r0 = op(i[0]) ^ i[3]
    r1 = op(r0) ^ i[2]
    r2 = op(r1) ^ i[1]
    r3 = op(r2) ^ i[0]
    r4 = op(r3)
    r5 = op(r4 ^ r0)
    r6 = op(r5 ^ r1)
    r7 = op(r6 ^ r2)
    return [(r3 ^ r7) & M16, (r2 ^ r6) & M16, (r1 ^ r5) & M16, (r0 ^ r4) & M16]


def prep(uid, x, y):
    return [(w(uid[5], uid[4]) + x + y) & M16, (w(uid[3], uid[2]) + KEYS[2]) & M16,
            (w(uid[1], uid[0]) + x * 2) & M16, 0x241A ^ KEYS[3]]


def prep2(uid, i1, i2, i3, i4):
    return [(w(uid[5], uid[4]) + i1) & M16, (w(uid[3], uid[2]) + i2) & M16,
            (w(uid[1], uid[0]) + i3 + KEYS[2]) & M16, (i4 + KEYS[3]) & M16]


def useful(uid, x, y):
    k = process_crypto(prep(uid, x, y))
    r1, r2 = k[0] ^ 0x4163, k[1] ^ 0x4344
    return [r1 & 0xFF, (r1 >> 8) & 0xFF, r2 & 0xFF, (r2 >> 8) & 0xFF]


def le(words):
    out = []
    for v in words:
        out += [v & 0xFF, (v >> 8) & 0xFF]
    return out


TABLE = []
for n in range(256):  # reflected CRC table, poly 0x8408 (== GlucoseDirect's literal crc16table)
    c = n
    for _ in range(8):
        c = (c >> 1) ^ 0x8408 if c & 1 else c >> 1
    TABLE.append(c)


def crc16(data):  # GlucoseDirect crc16: table reduce, bit-reverse, byteSwapped
    crc = 0xFFFF
    for b in data:
        crc = (crc >> 8) ^ TABLE[(crc ^ b) & 0xFF]
    rev = 0
    for _ in range(16):
        rev = (rev << 1) | (crc & 1)
        crc >>= 1
    return ((rev & 0xFF) << 8) | (rev >> 8)


def bswap(v):
    return ((v & 0xFF) << 8) | (v >> 8)


def decrypt_fram(uid, pi, fram):
    out = []
    for i in range(43):
        y = w(pi[5], pi[4])
        if i < 3 or i >= 40:
            y = 0xCADC
        if pi[0] == 0xE5:
            s1 = (w(uid[5], uid[4]) + y + i) & M16
        else:
            s1 = ((w(uid[5], uid[4]) + (w(pi[5], pi[4]) ^ 0x44)) + i) & M16
        s2 = (w(uid[3], uid[2]) + KEYS[2]) & M16
        s3 = (w(uid[1], uid[0]) + (i << 1)) & M16
        s4 = 0x241A ^ KEYS[3]
        key = le(process_crypto([s1, s2, s3, s4]))
        out += [fram[i * 8 + k] ^ key[k] for k in range(8)]
    return out


def ble_key(uid, data01):
    d = useful(uid, 0x1B, 0x1B6A)
    x = (w(d[1], d[0]) ^ w(d[3], d[2])) | 0x63
    y = w(data01[1], data01[0]) ^ 0x63
    k = process_crypto(prep(uid, x, y))
    key = []
    for _ in range(8):
        key += le(k)
        k = process_crypto(k)
    return key


def unlock_payload(uid, pi, enable_time, count):
    t = (enable_time + count) & 0xFFFFFFFF
    b = [(t >> (8 * i)) & 0xFF for i in range(4)]
    ad = useful(uid, 0x1B, 0x1B6A)
    ed = useful(uid, 0x1E, (enable_time & 0xFFFF) ^ w(pi[5], pi[4]))
    t11 = w(ed[1], ed[0]) ^ w(b[3], b[2])
    t12 = w(ad[1], ad[0])
    t13 = w(ed[3], ed[2]) ^ w(b[1], b[0])
    t14 = w(ad[3], ad[2])
    t2 = process_crypto(prep2(uid, t11, t12, t13, t14))
    t31 = bswap(crc16([0xC1, 0xC4, 0xC3, 0xC0, 0xD4, 0xE1, 0xE7, 0xBA, t2[0] & 0xFF, (t2[0] >> 8) & 0xFF]))
    t32 = bswap(crc16([t2[1] & 0xFF, t2[1] >> 8, t2[2] & 0xFF, t2[2] >> 8, t2[3] & 0xFF, t2[3] >> 8]))
    t33 = bswap(crc16([ad[0], ad[1], ad[2], ad[3], ed[0], ed[1]]))
    t34 = bswap(crc16([ed[2], ed[3], b[0], b[1], b[2], b[3]]))
    t4 = process_crypto(prep2(uid, t31, t32, t33, t34))
    return b + le(t4)


def enable_params(uid, pi, code):
    b = [(code >> (8 * i)) & 0xFF for i in range(4)]
    y = w(pi[5], pi[4]) ^ w(b[1], b[0])
    return [0x1E] + b + useful(uid, 0x1E, y)


def read_bits(buf, byte_off, bit_off, count):
    res = 0
    for i in range(count):
        total = byte_off * 8 + bit_off + i
        byte, bit = total // 8, total % 8
        if 0 <= byte < len(buf) and (buf[byte] >> bit) & 1:
            res |= 1 << i
    return res


def tables(swift_path):
    src = open(swift_path, encoding='utf-8').read()
    def t(name):
        body = re.search(r'private let ' + name + r' = \[(.*?)\]', src, re.S).group(1)
        return [float(v) for v in body.replace('\n', ' ').split(',') if v.strip()]
    return t('t1'), t('t2')


def calibrate(t1, t2, i2, i3, i4, i6, raw, temp, adj):
    r = temp * 72500.0 / (adj + i6) - 1000.0
    lr = math.log(r)
    d = lr ** 3 * 0.00000005283566 + lr ** 2 * 0.0000007061775 + lr * 0.0001964561 + 0.0009180023
    temperature = 1 / d - 273.15
    g = 65.0 * (raw - i3) / (i4 - i3) * 1.045 ** (32.5 - temperature)
    return round((g - t1[i2 - 1]) / t2[i2 - 1])  # values are positive here, so Python's round == Swift's


def hexs(bs):
    return ''.join('%02X' % b for b in bs)


if __name__ == '__main__':
    t1, t2 = tables(sys.argv[1])
    rnd = random.Random(20261003)
    uid = [rnd.randrange(256) for _ in range(6)] + [0x07, 0xE0]
    pi = [0x9D, 0x08, 0x30, 0x01, rnd.randrange(256), rnd.randrange(256)]
    fram = [rnd.randrange(256) for _ in range(344)]
    print('UID = "%s"' % hexs(uid))
    print('PATCH = "%s"' % hexs(pi))
    print('FRAM_IN = "%s"' % hexs(fram))
    print('FRAM_OUT = "%s"' % hexs(decrypt_fram(uid, pi, fram)))
    print('UNLOCK_1 = "%s"' % hexs(unlock_payload(uid, pi, 42, 1)))
    print('UNLOCK_300 = "%s"' % hexs(unlock_payload(uid, pi, 42, 300)))
    print('ENABLE = "%s"' % hexs(enable_params(uid, pi, 42)))
    print('CRC_123456789 = 0x%04X' % crc16(b'123456789'))

    # A valid encrypted BLE packet: plaintext with CRC, XORed with the keystream for header bytes 0..1.
    plain = [rnd.randrange(256) for _ in range(40)] + [0x34, 0x12]  # age 0x1234 at bytes 40..41
    c = crc16(plain)
    plain += [c >> 8, c & 0xFF]
    head = [rnd.randrange(256), rnd.randrange(256)]
    key = ble_key(uid, head)
    packet = head + [plain[i] ^ key[i] for i in range(44)]
    print('BLE_IN = "%s"' % hexs(packet))
    print('BLE_OUT = "%s"' % hexs(plain))

    # Calibration on hand-picked, physically plausible parameters/records.
    for (i2, i3, i4, i6, raw, temp, adj) in [(500, 20.0, 9000.0, 6000.0, 1800, 6500, 0), (200, -5.0, 8000.0, 5800.0, 1200, 6600, 40), (900, 0.0, 10000.0, 6200.0, 3000, 6400, -40)]:
        print('CAL(%d, %.1f, %.1f, %.1f, %d, %d, %d) = %d' % (i2, i3, i4, i6, raw, temp, adj, calibrate(t1, t2, i2, i3, i4, i6, raw, temp, adj)))
