package org.apache.james.mailbox.backup;

import java.io.IOException;
import java.io.InputStream;

public interface MailArchiveLoader {
    MailArchiveIterator load(InputStream inputStream) throws IOException;
}
