package org.apache.james.mailbox.backup;

import java.io.Closeable;
import java.util.Iterator;

public interface MailArchiveIterator extends Iterator<MailArchiveEntry>, Closeable {

}