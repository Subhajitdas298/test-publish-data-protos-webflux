package com.github.subhajitdas298.testpublishdataprotoswebflux.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPOutputStream;

public final class Gzip {

    private Gzip() {
    }

    public static byte[] compress(byte[] input) {
        var out = new ByteArrayOutputStream(input.length);
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
