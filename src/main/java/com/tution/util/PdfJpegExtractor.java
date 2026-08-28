package com.tution.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the embedded JPEG page images from a PDF — pure JDK, no poppler/Ghostscript,
 * no extra JARs. Works because scanned answer-sheet PDFs store each page as a /DCTDecode
 * (JPEG) stream; we walk the JPEG markers from each SOI to its EOI so an EXIF thumbnail
 * inside a page does not split the page in two.
 */
public class PdfJpegExtractor {

    /** True if the bytes start with the %PDF signature. */
    public static boolean isPdf(byte[] b) {
        return b != null && b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    /** Returns each full-page JPEG as a byte[] (small thumbnails are skipped). */
    public static List<byte[]> extract(byte[] b) {
        List<byte[]> out = new ArrayList<>();
        int i = 0, n = b.length;
        while (i < n - 2) {
            if ((b[i] & 0xFF) == 0xFF && (b[i + 1] & 0xFF) == 0xD8 && (b[i + 2] & 0xFF) == 0xFF) {
                int end = jpegEnd(b, i);
                if (end > i) {
                    int len = end - i;
                    if (len > 50_000) {                 // skip tiny thumbnails / icons
                        byte[] slice = new byte[len];
                        System.arraycopy(b, i, slice, 0, len);
                        out.add(slice);
                    }
                    i = end;                            // continue after this JPEG
                    continue;
                }
            }
            i++;
        }
        return out;
    }

    /** Walks JPEG markers from SOI at {@code start}; returns the index just past EOI, or -1. */
    private static int jpegEnd(byte[] b, int start) {
        int n = b.length, pos = start + 2;              // after FF D8
        while (pos < n - 1) {
            if ((b[pos] & 0xFF) != 0xFF) { pos++; continue; }
            int m;
            do { pos++; if (pos >= n) return -1; m = b[pos] & 0xFF; } while (m == 0xFF);
            pos++;                                      // past the marker byte
            if (m == 0xD9) return pos;                  // EOI
            if (m == 0x01 || (m >= 0xD0 && m <= 0xD7)) continue;   // standalone markers
            if (pos + 1 >= n) return -1;
            int len = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
            if (m == 0xDA) {                            // SOS: skip header, scan entropy to next marker
                pos += len;
                while (pos < n - 1) {
                    if ((b[pos] & 0xFF) == 0xFF) {
                        int nx = b[pos + 1] & 0xFF;
                        if (nx != 0x00 && !(nx >= 0xD0 && nx <= 0xD7)) break;
                    }
                    pos++;
                }
            } else {
                pos += len;                             // skip this segment
            }
        }
        return -1;
    }

    private PdfJpegExtractor() { }
}
