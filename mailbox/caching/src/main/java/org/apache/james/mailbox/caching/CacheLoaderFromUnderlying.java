package org.apache.james.mailbox.caching;

public interface CacheLoaderFromUnderlying<KeyT, ValueT, UnderlyingT, ExceptT extends Throwable> {
    ValueT load(KeyT key, UnderlyingT underlying) throws ExceptT;
}
