"""A tiny QR code encoder (byte mode, error correction level M, versions 1-6), as SVG.

Enough for the pairing link the dashboard shows (up to 106 bytes). Standard library
only, like the doorbell. Follows ISO/IEC 18004; the structure mirrors Project Nayuki's
reference implementation.
"""

# Level M, per version: (EC codewords per block, number of blocks, data codewords per block)
_BLOCKS = {1: (10, 1, 16), 2: (16, 1, 28), 3: (26, 1, 44), 4: (18, 2, 32), 5: (24, 2, 43), 6: (16, 4, 27)}
_ALIGN = {1: [], 2: [6, 18], 3: [6, 22], 4: [6, 26], 5: [6, 30], 6: [6, 34]}

# GF(256) with the QR polynomial x^8 + x^4 + x^3 + x^2 + 1
_EXP = [0] * 512
_LOG = [0] * 256
_x = 1
for _i in range(255):
    _EXP[_i] = _x
    _LOG[_x] = _i
    _x <<= 1
    if _x & 0x100:
        _x ^= 0x11D
for _i in range(255, 512):
    _EXP[_i] = _EXP[_i - 255]


def _mul(a, b):
    return 0 if a == 0 or b == 0 else _EXP[_LOG[a] + _LOG[b]]


def _rs_ecc(data, n):
    """Reed-Solomon error correction codewords for one block."""
    gen = [1]
    for i in range(n):  # multiply by (x - a^i)
        gen = [a ^ _mul(b, _EXP[i]) for a, b in zip(gen + [0], [0] + gen)]
    rem = [0] * n
    for byte in data:
        factor = byte ^ rem[0]
        rem = rem[1:] + [0]
        for i in range(n):
            rem[i] ^= _mul(gen[i + 1], factor)
    return rem


def _codewords(payload):
    for version in range(1, 7):
        ec, blocks, per_block = _BLOCKS[version]
        capacity = blocks * per_block
        if 4 + 8 + 8 * len(payload) <= capacity * 8:
            break
    else:
        raise ValueError("too long for a version 1-6 QR code")
    bits = [0, 1, 0, 0] + [(len(payload) >> i) & 1 for i in range(7, -1, -1)]
    for byte in payload:
        bits += [(byte >> i) & 1 for i in range(7, -1, -1)]
    bits += [0] * min(4, capacity * 8 - len(bits))
    bits += [0] * (-len(bits) % 8)
    data = [int("".join(map(str, bits[i:i + 8])), 2) for i in range(0, len(bits), 8)]
    pad = [0xEC, 0x11]
    while len(data) < capacity:
        data.append(pad[(len(data) - len(bits) // 8) % 2])
    split = [data[i * per_block:(i + 1) * per_block] for i in range(blocks)]
    eccs = [_rs_ecc(b, ec) for b in split]
    out = [b[i] for i in range(per_block) for b in split]
    out += [e[i] for i in range(ec) for e in eccs]
    return version, out


_MASKS = [
    lambda r, c: (r + c) % 2 == 0,
    lambda r, c: r % 2 == 0,
    lambda r, c: c % 3 == 0,
    lambda r, c: (r + c) % 3 == 0,
    lambda r, c: (r // 2 + c // 3) % 2 == 0,
    lambda r, c: (r * c) % 2 + (r * c) % 3 == 0,
    lambda r, c: ((r * c) % 2 + (r * c) % 3) % 2 == 0,
    lambda r, c: ((r + c) % 2 + (r * c) % 3) % 2 == 0,
]


def _function_patterns(version):
    size = 17 + 4 * version
    m = [[0] * size for _ in range(size)]
    fixed = [[False] * size for _ in range(size)]

    def put(r, c, dark):
        m[r][c] = int(dark)
        fixed[r][c] = True

    for i in range(size):  # timing
        put(6, i, i % 2 == 0)
        put(i, 6, i % 2 == 0)
    for fr, fc in ((3, 3), (3, size - 4), (size - 4, 3)):  # finders + separators
        for dr in range(-4, 5):
            for dc in range(-4, 5):
                r, c = fr + dr, fc + dc
                if 0 <= r < size and 0 <= c < size:
                    d = max(abs(dr), abs(dc))
                    put(r, c, d not in (2, 4))
    pos = _ALIGN[version]
    for r in pos:
        for c in pos:
            if (r, c) in ((6, 6), (6, pos[-1]), (pos[-1], 6)):
                continue  # would overlap a finder
            for dr in range(-2, 3):
                for dc in range(-2, 3):
                    put(r + dr, c + dc, max(abs(dr), abs(dc)) != 1)
    _format(m, fixed, size, 0, reserve=True)
    return m, fixed, size


def _format(m, fixed, size, mask, reserve=False):
    data = (0b00 << 3) | mask  # level M = 00
    rem = data
    for _ in range(10):
        rem = (rem << 1) ^ ((rem >> 9) * 0x537)
    bits = ((data << 10) | rem) ^ 0x5412

    def put(c, r, i):  # column, row, bit index
        m[r][c] = (bits >> i) & 1
        if reserve:
            fixed[r][c] = True

    for i in range(6):
        put(8, i, i)
    put(8, 7, 6)
    put(8, 8, 7)
    put(7, 8, 8)
    for i in range(9, 15):
        put(14 - i, 8, i)
    for i in range(8):
        put(size - 1 - i, 8, i)
    for i in range(8, 15):
        put(8, size - 15 + i, i)
    m[size - 8][8] = 1  # the dark module
    fixed[size - 8][8] = True


def _penalty(m, size):
    score = 0
    lines = m + [list(col) for col in zip(*m)]
    for line in lines:
        run = 1
        for i in range(1, size + 1):
            if i < size and line[i] == line[i - 1]:
                run += 1
            else:
                if run >= 5:
                    score += 3 + run - 5
                run = 1
        s = "".join(map(str, line))
        score += 40 * (s.count("10111010000") + s.count("00001011101"))
    for r in range(size - 1):
        for c in range(size - 1):
            if m[r][c] == m[r][c + 1] == m[r + 1][c] == m[r + 1][c + 1]:
                score += 3
    dark = sum(map(sum, m))
    score += int(abs(dark * 100 / (size * size) - 50) // 5) * 10
    return score


def matrix(text):
    """The QR code for [text] as rows of 0/1 (1 = dark), without the quiet zone."""
    version, words = _codewords(text.encode("utf-8"))
    base, fixed, size = _function_patterns(version)
    bits = [(w >> i) & 1 for w in words for i in range(7, -1, -1)]
    i = 0
    right = size - 1
    while right >= 1:
        if right == 6:
            right = 5
        for vert in range(size):
            for j in range(2):
                c = right - j
                upward = ((right + 1) & 2) == 0
                r = size - 1 - vert if upward else vert
                if not fixed[r][c]:
                    base[r][c] = bits[i] if i < len(bits) else 0
                    i += 1
        right -= 2
    best = None
    for mask, fn in enumerate(_MASKS):
        m = [row[:] for row in base]
        for r in range(size):
            for c in range(size):
                if not fixed[r][c] and fn(r, c):
                    m[r][c] ^= 1
        _format(m, fixed, size, mask)
        p = _penalty(m, size)
        if best is None or p < best[0]:
            best = (p, m)
    return best[1]


def svg(text, quiet=4):
    m = matrix(text)
    n = len(m) + 2 * quiet
    path = "".join(f"M{c + quiet},{r + quiet}h1v1h-1z" for r, row in enumerate(m) for c, v in enumerate(row) if v)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {n} {n}" shape-rendering="crispEdges">'
            f'<rect width="{n}" height="{n}" fill="#fff"/><path d="{path}" fill="#000"/></svg>')
