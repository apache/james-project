package org.apache.james.mailbox.backup;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipInputStream;

public class ZipArchiveLoader implements MailArchiveLoader {
    @Override
    public MailArchiveIterator load(InputStream inputStream) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            return new MailAccountZipIterator(zis);
        }
    }
}
