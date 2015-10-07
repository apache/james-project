package org.apache.james.mailbox.caching.guava;

import org.apache.james.mailbox.caching.MailboxByPathCache;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.google.common.cache.Cache;

/**
 * Guava-based implementation of MailboxByPathCache.
 * Note: for efficiency/simplicity reasons the cache key is MailboxPath.toString()
 * That may help also make it compatible with other cache backends in the future.
 *
 * @param <Id>
 */
public class GuavaMailboxByPathCache<Id extends MailboxId> extends AbstractGuavaCache implements MailboxByPathCache<Id> {
	
	private final Cache<String, Mailbox<Id>> findMailboxByPathCache = BUILDER.build();

	private final MailboxByPathCacheWrapper wrapper;

	
	public GuavaMailboxByPathCache() {
		this.wrapper = new MailboxByPathCacheWrapper(findMailboxByPathCache);
	}
	
	@Override
	public Mailbox<Id> findMailboxByPath(MailboxPath mailboxName, MailboxMapper<Id> underlying) throws MailboxNotFoundException, MailboxException {
		
		return wrapper.get(mailboxName, underlying);
	}
	
//	alternative plain implementation - review and choose the better
//	public Mailbox<Id> findMailboxByPath(MailboxPath mailboxName, MailboxMapper<Id> underlying) throws MailboxNotFoundException, MailboxException {
//		Mailbox<Id> mailbox = findMailboxByPathCache.getIfPresent(mailboxName.toString());
//		if (mailbox != null)
//			return mailbox;
//		else {
//			mailbox = new MailboxByPathCacheLoaderFromUnderlying().load(mailboxName, underlying);
//			findMailboxByPathCache.put(mailboxName.toString(), mailbox);
//			return mailbox;
//		}
//	}

	

	@Override
	public void invalidate(Mailbox<Id> mailbox) {
		invalidate(new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName()));
	}
	
	@Override
	public void invalidate(MailboxPath mailboxPath) {
		wrapper.invalidate(mailboxPath);
	}


	//Does it make sense to define such loaders as separate classes for reuse?
//	class MailboxByPathCacheLoaderFromUnderlying implements CacheLoaderFromUnderlying<MailboxPath, Mailbox<Id>, MailboxMapper<Id>, MailboxException> {
//		@Override
//		public Mailbox<Id> load(MailboxPath mailboxName, MailboxMapper<Id> underlying) throws MailboxException {
//			return underlying.findMailboxByPath(mailboxName);
//		}
//	}

	class MailboxByPathCacheWrapper extends GuavaCacheWrapper<MailboxPath, Mailbox<Id>, MailboxMapper<Id>, String, MailboxException> {

		public MailboxByPathCacheWrapper(
				Cache<String, Mailbox<Id>> cache/*,
				MailboxByPathCacheLoaderFromUnderlying loader*/) {
			super(cache);
		}

		@Override
		public Mailbox<Id> load(MailboxPath mailboxName, MailboxMapper<Id> underlying) throws MailboxException {
			return underlying.findMailboxByPath(mailboxName);
		}

		@Override
		public String getKeyRepresentation(MailboxPath key) {
			return key.toString();
		}
		
	}
}
