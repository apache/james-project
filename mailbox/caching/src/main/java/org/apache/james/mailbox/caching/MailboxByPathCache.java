package org.apache.james.mailbox.caching;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Caches the MailboxPath -> Mailbox mapping
 * 
 * @param <Id>
 */
public interface MailboxByPathCache<Id extends MailboxId> {

	public abstract Mailbox<Id> findMailboxByPath(MailboxPath mailboxName,
			MailboxMapper<Id> underlying) throws MailboxNotFoundException,
			MailboxException;

	public abstract void invalidate(Mailbox<Id> mailbox);
	
	public abstract void invalidate(MailboxPath mailboxPath);

	// for the purpose of cascading the invalidations; does it make sense? 
	//public void connectTo(MailboxMetadataCache<Id> mailboxMetadataCache);

}
