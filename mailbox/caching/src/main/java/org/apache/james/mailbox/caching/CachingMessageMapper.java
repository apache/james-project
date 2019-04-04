package org.apache.james.mailbox.caching;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

/**
 * A MessageMapper implementation that uses a MailboxMetadataCache to cache the information
 * from the underlying MessageMapper
 *
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * This module lacks tests and is not used in James products hence the choice to deprecate it.
 */
@Deprecated
public class CachingMessageMapper implements MessageMapper {

    private final MessageMapper underlying;
    private final MailboxMetadataCache cache;

    public CachingMessageMapper(MessageMapper underlying, MailboxMetadataCache cache) {
        this.underlying = underlying;
        this.cache = cache;
    }

    @Override
    public Iterator<MessageUid> listAllMessageUids(Mailbox mailbox) throws MailboxException {
        return underlying.listAllMessageUids(mailbox);
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
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox,
                                                      MessageRange set,
                                                      org.apache.james.mailbox.store.mail.MessageMapper.FetchType type,
                                                      int limit) throws MailboxException {
        return underlying.findInMailbox(mailbox, set, type, limit);
    }

    @Override
    public List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) throws MailboxException {
        return underlying.retrieveMessagesMarkedForDeletion(mailbox, messageRange);
    }

    @Override
    public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) throws MailboxException {
        invalidateMetadata(mailbox);
        return underlying.deleteMessages(mailbox, uids);
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox)
            throws MailboxException {
        return cache.countMessagesInMailbox(mailbox, underlying);
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox)
            throws MailboxException {
        return cache.countUnseenMessagesInMailbox(mailbox, underlying);
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
        return MailboxCounters.builder()
            .count(countMessagesInMailbox(mailbox))
            .unseen(countUnseenMessagesInMailbox(mailbox))
            .build();
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message)
            throws MailboxException {
        invalidateMetadata(mailbox);
        underlying.delete(mailbox, message);

    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox)
            throws MailboxException {
        return cache.findFirstUnseenMessageUid(mailbox, underlying);
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox)
            throws MailboxException {
        // TODO can be meaningfully cached?
        return underlying.findRecentMessageUidsInMailbox(mailbox);
    }

    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message)
            throws MailboxException {
        invalidateMetadata(mailbox);
        return underlying.add(mailbox, message);
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator calculator, MessageRange set)
            throws MailboxException {
        //check if there are in fact any updates
        if (set.iterator().hasNext()) {
            invalidateMetadata(mailbox);
        }
        return underlying.updateFlags(mailbox, calculator, set);
    }


    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original)
            throws MailboxException {
        invalidateMetadata(mailbox);
        return underlying.copy(mailbox, original);
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return cache.getLastUid(mailbox, underlying);
    }

    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return cache.getHighestModSeq(mailbox, underlying);
    }

    private void invalidateMetadata(Mailbox mailbox) {
        cache.invalidate(mailbox);
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        throw new UnsupportedOperationException("Move is not yet supported");
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        return underlying.getApplicableFlag(mailbox);
    }
}
