package org.apache.james.mailbox.caching;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;

/**
 * A MessageMapper implementation that uses a MailboxMetadataCache to cache the information
 * from the underlying MessageMapper
 * 
 * @param <Id>
 */
public class CachingMessageMapper<Id extends MailboxId> implements MessageMapper<Id> {

	
	private MessageMapper<Id> underlying;
	private MailboxMetadataCache<Id> cache;

	public CachingMessageMapper(MessageMapper<Id> underlying, MailboxMetadataCache<Id> cache) {
		this.underlying = underlying;
		this.cache = cache;
	}
	
	@Override
	public void endRequest() {
		underlying.endRequest();
	}

	@Override
	public <T> T execute(Transaction<T> transaction) throws MailboxException {
		return underlying.execute(transaction);
	}

	@Override
	public Iterator<Message<Id>> findInMailbox(Mailbox<Id> mailbox,
			MessageRange set,
			org.apache.james.mailbox.store.mail.MessageMapper.FetchType type,
			int limit) throws MailboxException {
		return underlying.findInMailbox(mailbox, set, type, limit);
	}

	@Override
	public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(
			Mailbox<Id> mailbox, MessageRange set) throws MailboxException {
		invalidateMetadata(mailbox);
		return underlying.expungeMarkedForDeletionInMailbox(mailbox, set);
	}

	@Override
	public long countMessagesInMailbox(Mailbox<Id> mailbox)
			throws MailboxException {
		return cache.countMessagesInMailbox(mailbox, underlying);
	}

	@Override
	public long countUnseenMessagesInMailbox(Mailbox<Id> mailbox)
			throws MailboxException {
		return cache.countUnseenMessagesInMailbox(mailbox, underlying);
	}

	@Override
	public void delete(Mailbox<Id> mailbox, Message<Id> message)
			throws MailboxException {
		invalidateMetadata(mailbox);
		underlying.delete(mailbox, message);
		
	}

	@Override
	public Long findFirstUnseenMessageUid(Mailbox<Id> mailbox)
			throws MailboxException {
		return cache.findFirstUnseenMessageUid(mailbox, underlying);
	}

	@Override
	public List<Long> findRecentMessageUidsInMailbox(Mailbox<Id> mailbox)
			throws MailboxException {
		// TODO can be meaningfully cached?
		return underlying.findRecentMessageUidsInMailbox(mailbox);
	}

	@Override
	public MessageMetaData add(Mailbox<Id> mailbox, Message<Id> message)
			throws MailboxException {
		invalidateMetadata(mailbox);
		return underlying.add(mailbox, message);
	}

	@Override
	public Iterator<UpdatedFlags> updateFlags(Mailbox<Id> mailbox, FlagsUpdateCalculator calculator, MessageRange set)
			throws MailboxException {
		//check if there are in fact any updates
		if (set.iterator().hasNext())
			invalidateMetadata(mailbox);
		return underlying.updateFlags(mailbox, calculator, set);
	}


	@Override
	public MessageMetaData copy(Mailbox<Id> mailbox, Message<Id> original)
			throws MailboxException {
		invalidateMetadata(mailbox);
		return underlying.copy(mailbox, original);
	}

	@Override
	public long getLastUid(Mailbox<Id> mailbox) throws MailboxException {
		return cache.getLastUid(mailbox, underlying);
	}

	@Override
	public long getHighestModSeq(Mailbox<Id> mailbox) throws MailboxException {
		return cache.getHighestModSeq(mailbox, underlying);
	}

	private void invalidateMetadata(Mailbox<Id> mailbox) {
		cache.invalidate(mailbox);
		
	}

    @Override
    public MessageMetaData move(Mailbox<Id> mailbox, Message<Id> original) throws MailboxException {
        throw new UnsupportedOperationException("Move is not yet supported");
    }

}
