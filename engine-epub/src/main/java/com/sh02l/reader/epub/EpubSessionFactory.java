package com.sh02l.reader.epub;

import java.io.File;
import java.io.IOException;

/** Reuses one verified publication's package data, within a conservative 2 MiB estimate. */
public final class EpubSessionFactory {
    private static final long BUDGET = 2L * 1024 * 1024;
    private String identity;
    private EpubArchive.Metadata metadata;
    private long epoch;

    /** Caller must verify the file's complete content digest before supplying that identity. */
    public EpubDocumentSession open(File file, String verifiedIdentity) throws IOException {
        if (verifiedIdentity == null || verifiedIdentity.isEmpty())
            throw new IllegalArgumentException("Verified identity required");
        final EpubArchive.Metadata reusable;
        final long token;
        synchronized (this) {
            token = epoch;
            reusable = verifiedIdentity.equals(identity) ? metadata : null;
            if (reusable == null) {
                identity = null;
                metadata = null;
            }
        }
        EpubArchive archive = new EpubArchive(file, reusable);
        EpubArchive.Metadata parsed =
                reusable == null ? new EpubArchive.Metadata(archive) : reusable;
        synchronized (this) {
            if (epoch == token && parsed.retainedBytes <= BUDGET) {
                identity = verifiedIdentity;
                metadata = parsed;
            }
        }
        return new EpubDocumentSession(archive);
    }

    /** Does no I/O and can run on the UI thread while an opening job is in progress. */
    public synchronized void clear() {
        epoch++;
        identity = null;
        metadata = null;
    }
}
