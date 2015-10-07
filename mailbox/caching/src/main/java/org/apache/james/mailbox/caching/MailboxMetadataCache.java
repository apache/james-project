package org.apache.james.mailbox.caching;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Caches the simple yet possibly expensive to compute metadata info 
 * about a Mailbox like all/unseen messages count and similar
 * 
 * @param <Id>
 */
public interface MailboxMetadataCache<Id extends MailboxId> {

	public abstract long countMessagesInMailbox(Mailbox<Id> mailbox,
			MessageMapper<Id> underlying) throws MailboxException;

	public abstract long countUnseenMessagesInMailbox(Mailbox<Id> mailbox,
			MessageMapper<Id> underlying) throws MailboxException;

	public abstract Long findFirstUnseenMessageUid(Mailbox<Id> mailbox,
			MessageMapper<Id> underlying) throws MailboxException;

	public abstract long getLastUid(Mailbox<Id> mailbox,
			MessageMapper<Id> underlying) throws MailboxException;

	public abstract long getHighestModSeq(Mailbox<Id> mailbox,
			MessageMapper<Id> underlying) throws MailboxException;

	public abstract void invalidate(Mailbox<Id> mailbox);

//	public abstract void invalidate(MailboxPath mailboxPath);

}