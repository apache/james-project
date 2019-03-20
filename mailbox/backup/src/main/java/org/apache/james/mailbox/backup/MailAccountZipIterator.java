package org.apache.james.mailbox.backup;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailAccountZipIterator implements MailArchiveIterator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailAccountZipIterator.class);
    private final ZipInputStream zipInputStream;

    private ZipEntry next;

    public MailAccountZipIterator(ZipInputStream inputStream) throws IOException {
        zipInputStream = inputStream;
        next = zipInputStream.getNextEntry();
    }

    @Override
    public void close() throws IOException {
        zipInputStream.close();
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public MailArchiveEntry next() {
        MailArchiveEntry mailArchiveEntry = from(next);
        try {
            next = zipInputStream.getNextEntry();
        } catch (IOException e) {
            LOGGER.error("Error during iterating on mail account zip", e);
        }
        return mailArchiveEntry;
    }

    private MailArchiveEntry from(ZipEntry zipEntry) {
        throw new NotImplementedException("TODO where MailArchiveEntry could be MailboxEntry, MailboxAnnotationEntry or MessageEntry");
    }
}
