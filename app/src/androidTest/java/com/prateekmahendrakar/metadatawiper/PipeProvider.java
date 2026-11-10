package com.prateekmahendrakar.metadatawiper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/*
 * Serves files the way a cloud provider does: through a pipe, which can't seek, with the size
 * known up front. "fixtures/<name>" streams a fixture; "endless" streams zeros until the reader
 * gives up, to stand in for a download that would take minutes.
 *
 * Java, not Kotlin: providers run in the test package's own process, which has no Kotlin
 * standard library.
 */
public class PipeProvider extends ContentProvider {

    // region State

    public static final String AUTHORITY = "com.prateekmahendrakar.metadatawiper.test.pipes";
    public static final long ENDLESS_SIZE = 4L * 1024 * 1024 * 1024;

    private static final String ENDLESS = "endless";
    private static final int CHUNK_SIZE = 64 * 1024;

    // endregion State

    // region Methods

    public static Uri uriFor(String path) {
        return Uri.parse("content://" + AUTHORITY + "/" + path);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri,
                        String[] projection,
                        String selection,
                        String[] selectionArgs,
                        String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
        cursor.addRow(new Object[] { uri.getLastPathSegment(), sizeOf(uri) });
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri,
                                         String mode) throws java.io.FileNotFoundException {
        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            throw new java.io.FileNotFoundException(e.getMessage());
        }
        ParcelFileDescriptor writeSide = pipe[1];
        new Thread(() -> write(uri, writeSide)).start();
        return pipe[0];
    }

    private void write(Uri uri,
                       ParcelFileDescriptor writeSide) {
        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
            if (isEndless(uri)) {
                byte[] chunk = new byte[CHUNK_SIZE];
                while (true) {
                    output.write(chunk);
                }
            }
            try (InputStream input = openFixture(uri)) {
                byte[] chunk = new byte[CHUNK_SIZE];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    output.write(chunk, 0, count);
                }
            }
        } catch (IOException ignored) {
            // The reader closed its end, which is how a cancelled copy looks from here.
        }
    }

    private long sizeOf(Uri uri) {
        if (isEndless(uri)) {
            return ENDLESS_SIZE;
        }
        try (InputStream input = openFixture(uri)) {
            long size = 0;
            byte[] chunk = new byte[CHUNK_SIZE];
            int count;
            while ((count = input.read(chunk)) != -1) {
                size += count;
            }
            return size;
        } catch (IOException e) {
            return 0;
        }
    }

    private boolean isEndless(Uri uri) {
        return ENDLESS.equals(uri.getPathSegments().get(0));
    }

    private InputStream openFixture(Uri uri) throws IOException {
        return getContext().getAssets().open("fixtures/" + uri.getLastPathSegment());
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri,
                      ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri,
                      String selection,
                      String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri,
                      ContentValues values,
                      String selection,
                      String[] selectionArgs) {
        return 0;
    }

    // endregion Methods
}
