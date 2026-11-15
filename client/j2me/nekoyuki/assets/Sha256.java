package nekoyuki.assets;

/**
 * SHA-256 thuan Java cho J2ME (CLDC khong co san MessageDigest).
 * Chi dung phep tinh so nguyen, chay duoc tren moi may ao Java.
 */
public final class Sha256 {

    private static final int[] K = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    private final int[] h = new int[8];
    private final int[] w = new int[64];
    private final byte[] block = new byte[64];
    private int blockLen;
    private long total;

    public Sha256() {
        reset();
    }

    public void reset() {
        h[0] = 0x6a09e667; h[1] = 0xbb67ae85; h[2] = 0x3c6ef372; h[3] = 0xa54ff53a;
        h[4] = 0x510e527f; h[5] = 0x9b05688c; h[6] = 0x1f83d9ab; h[7] = 0x5be0cd19;
        blockLen = 0;
        total = 0;
    }

    public void update(byte[] data, int off, int len) {
        total += len;
        while (len > 0) {
            int n = 64 - blockLen;
            if (n > len) n = len;
            System.arraycopy(data, off, block, blockLen, n);
            blockLen += n;
            off += n;
            len -= n;
            if (blockLen == 64) {
                compress();
                blockLen = 0;
            }
        }
    }

    /** Ket thuc va tra ve 32 byte ma hash. Sau do doi tuong duoc dat lai. */
    public byte[] digest() {
        long bits = total * 8;
        block[blockLen++] = (byte) 0x80;
        if (blockLen > 56) {
            while (blockLen < 64) block[blockLen++] = 0;
            compress();
            blockLen = 0;
        }
        while (blockLen < 56) block[blockLen++] = 0;
        for (int i = 7; i >= 0; i--) block[blockLen++] = (byte) (bits >>> (i * 8));
        compress();
        byte[] out = new byte[32];
        for (int i = 0; i < 8; i++) {
            out[i * 4] = (byte) (h[i] >>> 24);
            out[i * 4 + 1] = (byte) (h[i] >>> 16);
            out[i * 4 + 2] = (byte) (h[i] >>> 8);
            out[i * 4 + 3] = (byte) h[i];
        }
        reset();
        return out;
    }

    /** Ket thuc va tra ve ma hash dang chuoi hex 64 ky tu (chu thuong). */
    public String digestHex() {
        byte[] d = digest();
        String digits = "0123456789abcdef";
        StringBuffer sb = new StringBuffer(64);
        for (int i = 0; i < d.length; i++) {
            sb.append(digits.charAt((d[i] >> 4) & 15));
            sb.append(digits.charAt(d[i] & 15));
        }
        return sb.toString();
    }

    private void compress() {
        for (int i = 0; i < 16; i++) {
            w[i] = ((block[i * 4] & 255) << 24) | ((block[i * 4 + 1] & 255) << 16)
                 | ((block[i * 4 + 2] & 255) << 8) | (block[i * 4 + 3] & 255);
        }
        for (int i = 16; i < 64; i++) {
            int x = w[i - 15];
            int y = w[i - 2];
            int s0 = ((x >>> 7) | (x << 25)) ^ ((x >>> 18) | (x << 14)) ^ (x >>> 3);
            int s1 = ((y >>> 17) | (y << 15)) ^ ((y >>> 19) | (y << 13)) ^ (y >>> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
        for (int i = 0; i < 64; i++) {
            int s1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
            int ch = (e & f) ^ (~e & g);
            int t1 = hh + s1 + ch + K[i] + w[i];
            int s0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
            int maj = (a & b) ^ (a & c) ^ (b & c);
            int t2 = s0 + maj;
            hh = g; g = f; f = e; e = d + t1;
            d = c; c = b; b = a; a = t1 + t2;
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d;
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
    }
}
