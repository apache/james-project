package org.apache.james.imap.message;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;
import com.google.common.io.Files;

public class FileBackedLiteral implements Literal, Closeable {
    public static final int FILE_THRESHOLD = 100 * 1024;

    public static FileBackedLiteral copy(InputStream stream) throws IOException {
        FileBackedOutputStream out = new FileBackedOutputStream(FILE_THRESHOLD);
        stream.transferTo(out);
        return new FileBackedLiteral(out.asByteSource(), out::reset);
    }

    public static FileBackedLiteral of(File file) {
        return new FileBackedLiteral(Files.asByteSource(file), file::delete);
    }

    public static FileBackedLiteral of(byte[] bytes) {
        return new FileBackedLiteral(ByteSource.wrap(bytes), () -> { });
    }

    private final ByteSource content;
    private final Closeable closeable;

    private FileBackedLiteral(ByteSource content, Closeable closeable) {
        this.content = content;
        this.closeable = closeable;
    }

    @Override
    public void close() throws IOException {
        closeable.close();
    }

    @Override
    public long size() throws IOException {
        return content.size();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return content.openStream();
    }
}
