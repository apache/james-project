package org.apache.james.mailbox.store.quota;

import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedOperationException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;

import com.google.common.collect.ImmutableMap;

/**
 * {@link MaxQuotaManager} which use the same quota for all users.
 *
 * By default this means not quota at all
 */
public class FixedMaxQuotaManager implements MaxQuotaManager {

    private Optional<QuotaSize> maxStorage = Optional.empty();
    private Optional<QuotaCount> maxMessage = Optional.empty();

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSize maxStorageQuota) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCount maxMessageCount) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCount count) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSize size) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void setGlobalMaxStorage(QuotaSize globalMaxStorage) {
        maxStorage = Optional.of(globalMaxStorage);
    }

    @Override
    public void removeGlobalMaxStorage() throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void removeGlobalMaxMessage() {
        maxMessage = Optional.empty();
    }

    @Override
    public void setGlobalMaxMessage(QuotaCount globalMaxMessageCount) {
        maxMessage = Optional.empty();
    }

    @Override
    public Optional<QuotaSize> getMaxStorage(QuotaRoot quotaRoot) {
        return maxStorage;
    }

    @Override
    public Optional<QuotaCount> getMaxMessage(QuotaRoot quotaRoot) {
        return maxMessage;
    }

    @Override
    public Map<Quota.Scope, QuotaCount> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return maxMessage
            .map(value -> ImmutableMap.of(Quota.Scope.Global, value))
            .orElse(ImmutableMap.of());
    }

    @Override
    public Map<Quota.Scope, QuotaSize> listMaxStorageDetails(QuotaRoot quotaRoot) {
        return maxStorage
            .map(value -> ImmutableMap.of(Quota.Scope.Global, value))
            .orElse(ImmutableMap.of());
    }

    @Override
    public Optional<QuotaCount> getDomainMaxMessage(Domain domain) {
        return Optional.empty();
    }

    @Override
    public Optional<QuotaSize> getDomainMaxStorage(Domain domain) {
        return Optional.empty();
    }

    public Optional<QuotaCount> getMaxMessage() {
        return Optional.empty();
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Optional<QuotaSize> getGlobalMaxStorage() {
        return maxStorage;
    }

    @Override
    public Optional<QuotaCount> getGlobalMaxMessage() {
        return maxMessage;
    }
}
