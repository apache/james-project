package org.apache.james.mailbox.store.quota;

import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedOperationException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

/**
 * {@link MaxQuotaManager} which use the same quota for all users.
 *
 * By default this means not quota at all
 */
public class FixedMaxQuotaManager implements MaxQuotaManager {

    private Optional<QuotaSizeLimit> maxStorage = Optional.empty();
    private Optional<QuotaCountLimit> maxMessage = Optional.empty();

    @Override
    public void setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> setMaxStorageReactive(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> setMaxMessageReactive(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void removeMaxMessage(QuotaRoot quotaRoot) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> removeMaxMessageReactive(QuotaRoot quotaRoot) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void removeMaxStorage(QuotaRoot quotaRoot) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> removeMaxStorageReactive(QuotaRoot quotaRoot) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void setDomainMaxMessage(Domain domain, QuotaCountLimit count) throws MailboxException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> setDomainMaxMessageReactive(Domain domain, QuotaCountLimit count) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> setDomainMaxStorageReactive(Domain domain, QuotaSizeLimit size) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) {
        maxStorage = Optional.of(globalMaxStorage);
    }

    @Override
    public Publisher<Void> setGlobalMaxStorageReactive(QuotaSizeLimit globalMaxStorage) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void removeGlobalMaxStorage() throws MailboxException {
        throw new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> removeGlobalMaxStorageReactive() {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void removeGlobalMaxMessage() {
        maxMessage = Optional.empty();
    }

    @Override
    public Publisher<Void> removeGlobalMaxMessageReactive() {
        return Mono.fromRunnable(() -> maxMessage = Optional.empty());
    }

    @Override
    public void setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) {
        maxMessage = Optional.empty();
    }

    @Override
    public Publisher<Void> setGlobalMaxMessageReactive(QuotaCountLimit globalMaxMessageCount) {
        return Mono.fromRunnable(() -> maxMessage = Optional.empty());
    }

    @Override
    public Optional<QuotaSizeLimit> getMaxStorage(QuotaRoot quotaRoot) {
        return maxStorage;
    }

    @Override
    public Optional<QuotaCountLimit> getMaxMessage(QuotaRoot quotaRoot) {
        return maxMessage;
    }

    @Override
    public Map<Quota.Scope, QuotaCountLimit> listMaxMessagesDetails(QuotaRoot quotaRoot) {
        return maxMessage
            .map(value -> ImmutableMap.of(Quota.Scope.Global, value))
            .orElse(ImmutableMap.of());
    }

    @Override
    public Map<Quota.Scope, QuotaSizeLimit> listMaxStorageDetails(QuotaRoot quotaRoot) {
        return maxStorage
            .map(value -> ImmutableMap.of(Quota.Scope.Global, value))
            .orElse(ImmutableMap.of());
    }

    @Override
    public Optional<QuotaCountLimit> getDomainMaxMessage(Domain domain) {
        return Optional.empty();
    }

    @Override
    public Publisher<QuotaCountLimit> getDomainMaxMessageReactive(Domain domain) {
        return Mono.empty();
    }

    @Override
    public Optional<QuotaSizeLimit> getDomainMaxStorage(Domain domain) {
        return Optional.empty();
    }

    @Override
    public Publisher<QuotaSizeLimit> getDomainMaxStorageReactive(Domain domain) {
        return Mono.empty();
    }

    @Override
    public void removeDomainMaxMessage(Domain domain) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> removeDomainMaxMessageReactive(Domain domain) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public void removeDomainMaxStorage(Domain domain) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Can not modify domain specific upper limit for FixedMaxQuotaManager");
    }

    @Override
    public Publisher<Void> removeDomainMaxStorageReactive(Domain domain) {
        return Mono.error(() -> new UnsupportedOperationException("Can not modify QuotaRoot specific upper limit for FixedMaxQuotaManager"));
    }

    @Override
    public Optional<QuotaSizeLimit> getGlobalMaxStorage() {
        return maxStorage;
    }

    @Override
    public Publisher<QuotaSizeLimit> getGlobalMaxStorageReactive() {
        return Mono.justOrEmpty(maxStorage);
    }

    @Override
    public Optional<QuotaCountLimit> getGlobalMaxMessage() {
        return maxMessage;
    }

    @Override
    public Publisher<QuotaCountLimit> getGlobalMaxMessageReactive() {
        return Mono.justOrEmpty(maxMessage);
    }
}
