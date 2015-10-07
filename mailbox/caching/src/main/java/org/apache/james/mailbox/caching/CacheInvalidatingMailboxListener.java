package org.apache.james.mailbox.caching;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxListenerSupport;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.MailboxId;
/**
 * A MailboxListener that invalidates the configured caches in response to Events
 * 
 * @param <Id>
 */
public class CacheInvalidatingMailboxListener<Id extends MailboxId> implements MailboxListener {

	private MailboxByPathCache<Id> mailboxCacheByPath;
	private MailboxMetadataCache<Id> mailboxMetadataCache;

	public CacheInvalidatingMailboxListener(MailboxByPathCache<Id> mailboxCacheByPath, MailboxMetadataCache<Id> mailboxMetadataCache) {
		this.mailboxCacheByPath = mailboxCacheByPath;
		this.mailboxMetadataCache = mailboxMetadataCache;
	}
	
	/**
	 * Used to register the CacheInvalidatingMailboxListener as a global listener 
	 * into the main MailboxListener
	 * 
	 * @param listener
	 * @throws MailboxException
	 */
	public void register(MailboxListenerSupport listener) throws MailboxException {
		listener.addGlobalListener(this, null);
	}
	
	@Override
	public void event(Event event) {
		// TODO this needs for sure to be smarter
		try {
			if (event instanceof MessageEvent) {
				// invalidate the metadata caches
					invalidateMetadata(event);
			}
			invalidateMailbox(event);
		} catch (MailboxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void invalidateMetadata(Event event) throws MailboxException {
		//HMM, race conditions welcome?
		mailboxMetadataCache.invalidate(mailboxCacheByPath.findMailboxByPath(event.getMailboxPath(), null));
		
	}

	private void invalidateMailbox(Event event) {
		mailboxCacheByPath.invalidate(event.getMailboxPath());		
	}

}
