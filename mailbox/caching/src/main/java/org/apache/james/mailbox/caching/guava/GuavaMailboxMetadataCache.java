package org.apache.james.mailbox.caching.guava;

import org.apache.james.mailbox.caching.MailboxMetadataCache;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.google.common.cache.Cache;
/**
 * Guava-based implementation of MailboxMetadataCache.
 * Note: for efficiency/simplicity reasons the cache key is Mailbox.getMailboxId()
 *
 * @param <Id>
 */

public class GuavaMailboxMetadataCache<Id extends MailboxId> extends AbstractGuavaCache implements MailboxMetadataCache<Id> {

	// TODO these can probably be instantiated more elegant way
	private final Cache<Id, Long> cacheCountMessagesInMailbox = BUILDER.build();
	private final Cache<Id, Long> cacheCountUnseenMessagesInMailbox = BUILDER.build();
	private final Cache<Id, Long> cacheFindFirstUnseenMessageUid = BUILDER.build();
	private final Cache<Id, Long> cacheGetLastUid = BUILDER.build();
	private final Cache<Id, Long> cacheGetHighestModSeq = BUILDER.build();

	private final MetadataCacheWrapper countMessagesInMailboxWrapper = new CountMessagesInMailboxWrapper(cacheCountMessagesInMailbox);
	private final MetadataCacheWrapper countUnseenMessagesInMailboxWrapper = new CountUnseenMessagesInMailboxWrapper(cacheCountUnseenMessagesInMailbox);
	private final MetadataCacheWrapper findFirstUnseenMessageUid = new FindFirstUnseenMessageUidWrapper(cacheFindFirstUnseenMessageUid);
	private final MetadataCacheWrapper highestModSeqWrapper = new HighestModseqCacheWrapper(cacheGetHighestModSeq);
	private final MetadataCacheWrapper lastUidWrapper = new LastUidCacheWrapper(cacheGetLastUid);
	
	@Override
	public long countMessagesInMailbox(Mailbox<Id> mailbox, MessageMapper<Id> underlying) throws MailboxException {
		return countMessagesInMailboxWrapper.get(mailbox, underlying);
	}
	
	@Override
	public long countUnseenMessagesInMailbox(Mailbox<Id> mailbox, MessageMapper<Id> underlying)
			throws MailboxException {
		return countUnseenMessagesInMailboxWrapper.get(mailbox, underlying);
	}
	
	@Override
	public Long findFirstUnseenMessageUid(Mailbox<Id> mailbox, MessageMapper<Id> underlying)
			throws MailboxException {
		return findFirstUnseenMessageUid.get(mailbox, underlying);
	}
	
	@Override
	public long getLastUid(Mailbox<Id> mailbox, MessageMapper<Id> underlying) throws MailboxException {
		return lastUidWrapper.get(mailbox, underlying);

	}
	
	@Override
	public long getHighestModSeq(Mailbox<Id> mailbox, MessageMapper<Id> underlying) throws MailboxException {
		return highestModSeqWrapper.get(mailbox, underlying);
	}
	
	@Override
	public void invalidate(Mailbox<Id> mailbox) {
		cacheCountMessagesInMailbox.invalidate(mailbox);
		cacheCountUnseenMessagesInMailbox.invalidate(mailbox);
		cacheFindFirstUnseenMessageUid.invalidate(mailbox);
		lastUidWrapper.invalidate(mailbox);
		highestModSeqWrapper.invalidate(mailbox);
	}

	
	abstract class MetadataCacheWrapper extends GuavaCacheWrapper<Mailbox<Id>, Long, MessageMapper<Id>, Id, MailboxException> {

		public MetadataCacheWrapper(Cache<Id, Long> cache) {
			super(cache);
		}

		@Override
		public Id getKeyRepresentation(Mailbox<Id> key) {
			return key.getMailboxId();
		}		
		
	}

	class CountMessagesInMailboxWrapper extends MetadataCacheWrapper {

		public CountMessagesInMailboxWrapper(Cache<Id, Long> cache) {
			super(cache);
		}
		@Override
		public Long load(Mailbox<Id> mailbox, MessageMapper<Id> underlying)
				throws MailboxException {
			return underlying.countMessagesInMailbox(mailbox);
		}

	}
	
	class CountUnseenMessagesInMailboxWrapper extends MetadataCacheWrapper {

		public CountUnseenMessagesInMailboxWrapper(Cache<Id, Long> cache) {
			super(cache);
		}
		@Override
		public Long load(Mailbox<Id> mailbox, MessageMapper<Id> underlying)
				throws MailboxException {
			return underlying.countUnseenMessagesInMailbox(mailbox);
		}

	}

	class FindFirstUnseenMessageUidWrapper extends MetadataCacheWrapper {

		public FindFirstUnseenMessageUidWrapper(Cache<Id, Long> cache) {
			super(cache);
		}
		@Override
		public Long load(Mailbox<Id> mailbox, MessageMapper<Id> underlying)
				throws MailboxException {
			return underlying.findFirstUnseenMessageUid(mailbox);
		}

	}

	class LastUidCacheWrapper extends MetadataCacheWrapper {
		public LastUidCacheWrapper(Cache<Id, Long> cache) {
			super(cache);
		}
		@Override
		public Long load(Mailbox<Id> mailbox, MessageMapper<Id> underlying) throws MailboxException {
			return underlying.getLastUid(mailbox);
		}
	}

	class HighestModseqCacheWrapper extends MetadataCacheWrapper {
		public HighestModseqCacheWrapper(Cache<Id, Long> cache) {
			super(cache);
		}
		@Override
		public Long load(Mailbox<Id> mailbox, MessageMapper<Id> underlying) throws MailboxException {
			return underlying.getHighestModSeq(mailbox);
		}
	}

}
