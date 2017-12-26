package org.apache.james.mailbox.caching.guava;

import org.apache.james.mailbox.caching.MailboxByPathCache;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;

import com.google.common.cache.Cache;

/**
 * Guava-based implementation of MailboxByPathCache.
 * Note: for efficiency/simplicity reasons the cache key is MailboxPath.toString()
 * That may help also make it compatible with other cache backends in the future.
 *
 */
public class GuavaMailboxByPathCache extends AbstractGuavaCache implements MailboxByPathCache {

    private final Cache<String, Mailbox> findMailboxByPathCache = BUILDER.build();

    private final MailboxByPathCacheWrapper wrapper;


    public GuavaMailboxByPathCache() {
        this.wrapper = new MailboxByPathCacheWrapper(findMailboxByPathCache);
    }

    @Override
    public Mailbox findMailboxByPath(MailboxPath mailboxName, MailboxMapper underlying) throws MailboxNotFoundException, MailboxException {

        return wrapper.get(mailboxName, underlying);
    }

//    alternative plain implementation - review and choose the better
//    public Mailbox findMailboxByPath(MailboxPath mailboxName, MailboxMapper underlying) throws MailboxNotFoundException, MailboxException {
//        Mailbox mailbox = findMailboxByPathCache.getIfPresent(mailboxName.toString());
//        if (mailbox != null)
//            return mailbox;
//        else {
//            mailbox = new MailboxByPathCacheLoaderFromUnderlying().load(mailboxName, underlying);
//            findMailboxByPathCache.put(mailboxName.toString(), mailbox);
//            return mailbox;
//        }
//    }



    @Override
    public void invalidate(Mailbox mailbox) {
        invalidate(mailbox.generateAssociatedPath());
    }

    @Override
    public void invalidate(MailboxPath mailboxPath) {
        wrapper.invalidate(mailboxPath);
    }


    //Does it make sense to define such loaders as separate classes for reuse?
//    class MailboxByPathCacheLoaderFromUnderlying implements CacheLoaderFromUnderlying<MailboxPath, Mailbox, MailboxMapper, MailboxException> {
//        @Override
//        public Mailbox load(MailboxPath mailboxName, MailboxMapper underlying) throws MailboxException {
//            return underlying.findMailboxByPath(mailboxName);
//        }
//    }

    class MailboxByPathCacheWrapper extends GuavaCacheWrapper<MailboxPath, Mailbox, MailboxMapper, String, MailboxException> {

        public MailboxByPathCacheWrapper(
                Cache<String, Mailbox> cache/*,
                MailboxByPathCacheLoaderFromUnderlying loader*/) {
            super(cache);
        }

        @Override
        public Mailbox load(MailboxPath mailboxName, MailboxMapper underlying) throws MailboxException {
            return underlying.findMailboxByPath(mailboxName);
        }

        @Override
        public String getKeyRepresentation(MailboxPath key) {
            return key.toString();
        }

    }
}
