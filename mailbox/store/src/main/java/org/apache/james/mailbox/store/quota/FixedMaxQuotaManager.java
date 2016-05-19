package org.apache.james.mailbox.store.quota;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedOperationException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;

/**
 * {@link MaxQuotaManager} which use the same quota for all users.
 *
 * By default this means not quota at all
 */
public class FixedMaxQuotaManager implements MaxQuotaManager {
    private long maxStorage = Quota.UNLIMITED;
    private long maxMessage = Quota.UNLIMITED;

    public void setMaxStorage(QuotaRoot quotaRoot, long maxStorageQuota) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    public void setMaxMessage(QuotaRoot quotaRoot, long maxMessageCount) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    public void setDefaultMaxStorage(long defaultMaxStorage) {
        maxStorage = defaultMaxStorage;
    }

    public void setDefaultMaxMessage(long defaultMaxMessageCount) {
        maxMessage = defaultMaxMessageCount;
    }

    public long getMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        return maxStorage;
    }

    public long getMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        return maxMessage;
    }

    public long getDefaultMaxStorage() throws MailboxException {
        return maxStorage;
    }

    public long getDefaultMaxMessage() throws MailboxException {
        return maxMessage;
    }
}
