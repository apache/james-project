package org.apache.james.mailbox.caching.guava;

import java.util.Optional;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.caching.MailboxMetadataCache;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.google.common.cache.Cache;
/**
 * Guava-based implementation of MailboxMetadataCache.
 * Note: for efficiency/simplicity reasons the cache key is Mailbox.getMailboxId()
 *
 */

public class GuavaMailboxMetadataCache extends AbstractGuavaCache implements MailboxMetadataCache {

    // TODO these can probably be instantiated more elegant way
    private final Cache<MailboxId, Long> cacheCountMessagesInMailbox = BUILDER.build();
    private final Cache<MailboxId, Long> cacheCountUnseenMessagesInMailbox = BUILDER.build();
    private final Cache<MailboxId, MessageUid> cacheFindFirstUnseenMessageUid = BUILDER.build();
    private final Cache<MailboxId, Optional<MessageUid>> cacheGetLastUid = BUILDER.build();
    private final Cache<MailboxId, Long> cacheGetHighestModSeq = BUILDER.build();

    private final MetadataCacheWrapper<Long> countMessagesInMailboxWrapper = new CountMessagesInMailboxWrapper(cacheCountMessagesInMailbox);
    private final MetadataCacheWrapper<Long> countUnseenMessagesInMailboxWrapper = new CountUnseenMessagesInMailboxWrapper(cacheCountUnseenMessagesInMailbox);
    private final MetadataCacheWrapper<MessageUid> findFirstUnseenMessageUid = new FindFirstUnseenMessageUidWrapper(cacheFindFirstUnseenMessageUid);
    private final MetadataCacheWrapper<Long> highestModSeqWrapper = new HighestModseqCacheWrapper(cacheGetHighestModSeq);
    private final MetadataCacheWrapper<Optional<MessageUid>> lastUidWrapper = new LastUidCacheWrapper(cacheGetLastUid);

    @Override
    public long countMessagesInMailbox(Mailbox mailbox, MessageMapper underlying) throws MailboxException {
        return countMessagesInMailboxWrapper.get(mailbox, underlying);
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox, MessageMapper underlying)
            throws MailboxException {
        return countUnseenMessagesInMailboxWrapper.get(mailbox, underlying);
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox, MessageMapper underlying)
            throws MailboxException {
        return findFirstUnseenMessageUid.get(mailbox, underlying);
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox, MessageMapper underlying) throws MailboxException {
        return lastUidWrapper.get(mailbox, underlying);

    }

    @Override
    public long getHighestModSeq(Mailbox mailbox, MessageMapper underlying) throws MailboxException {
        return highestModSeqWrapper.get(mailbox, underlying);
    }

    @Override
    public void invalidate(Mailbox mailbox) {
        cacheCountMessagesInMailbox.invalidate(mailbox);
        cacheCountUnseenMessagesInMailbox.invalidate(mailbox);
        cacheFindFirstUnseenMessageUid.invalidate(mailbox);
        lastUidWrapper.invalidate(mailbox);
        highestModSeqWrapper.invalidate(mailbox);
    }


    abstract class MetadataCacheWrapper<Value> extends GuavaCacheWrapper<Mailbox, Value, MessageMapper, MailboxId, MailboxException> {

        public MetadataCacheWrapper(Cache<MailboxId, Value> cache) {
            super(cache);
        }

        @Override
        public MailboxId getKeyRepresentation(Mailbox key) {
            return key.getMailboxId();
        }

    }

    class CountMessagesInMailboxWrapper extends MetadataCacheWrapper<Long> {

        public CountMessagesInMailboxWrapper(Cache<MailboxId, Long> cache) {
            super(cache);
        }
        @Override
        public Long load(Mailbox mailbox, MessageMapper underlying)
                throws MailboxException {
            return underlying.countMessagesInMailbox(mailbox);
        }

    }

    class CountUnseenMessagesInMailboxWrapper extends MetadataCacheWrapper<Long> {

        public CountUnseenMessagesInMailboxWrapper(Cache<MailboxId, Long> cache) {
            super(cache);
        }
        @Override
        public Long load(Mailbox mailbox, MessageMapper underlying)
                throws MailboxException {
            return underlying.countUnseenMessagesInMailbox(mailbox);
        }

    }

    class FindFirstUnseenMessageUidWrapper extends MetadataCacheWrapper<MessageUid> {

        public FindFirstUnseenMessageUidWrapper(Cache<MailboxId, MessageUid> cache) {
            super(cache);
        }
        @Override
        public MessageUid load(Mailbox mailbox, MessageMapper underlying)
                throws MailboxException {
            return underlying.findFirstUnseenMessageUid(mailbox);
        }

    }

    class LastUidCacheWrapper extends MetadataCacheWrapper<Optional<MessageUid>> {
        public LastUidCacheWrapper(Cache<MailboxId, Optional<MessageUid>> cache) {
            super(cache);
        }
        @Override
        public Optional<MessageUid> load(Mailbox mailbox, MessageMapper underlying) throws MailboxException {
            return underlying.getLastUid(mailbox);
        }
    }

    class HighestModseqCacheWrapper extends MetadataCacheWrapper<Long> {
        public HighestModseqCacheWrapper(Cache<MailboxId, Long> cache) {
            super(cache);
        }
        @Override
        public Long load(Mailbox mailbox, MessageMapper underlying) throws MailboxException {
            return underlying.getHighestModSeq(mailbox);
        }
    }

}
