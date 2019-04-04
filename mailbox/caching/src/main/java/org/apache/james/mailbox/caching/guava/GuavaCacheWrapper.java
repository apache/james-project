package org.apache.james.mailbox.caching.guava;

import org.apache.james.mailbox.caching.CacheLoaderFromUnderlying;

import com.google.common.cache.Cache;

/**
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * This module lacks tests and is not used in James products hence the choice to deprecate it.
 */
@Deprecated
public abstract class GuavaCacheWrapper<KeyT, ValueT, UnderlyingT, KeyRepresentationT, ExceptT extends Throwable>
    implements CacheLoaderFromUnderlying<KeyT, ValueT, UnderlyingT, ExceptT> {

    private final Cache<KeyRepresentationT, ValueT> cache;

    public GuavaCacheWrapper(Cache<KeyRepresentationT, ValueT> cache/*, CacheLoaderFromUnderlying<Key, Value, Underlying, Except> loader*/) {
        this.cache = cache;
    }

    public ValueT get(KeyT key, UnderlyingT underlying) throws ExceptT {
        ValueT value = cache.getIfPresent(getKeyRepresentation(key));
        if (value != null) {
            return value;
        } else {
            value = load(key, underlying);
            if (value != null) {
                cache.put(getKeyRepresentation(key), value);
            }
            return value;
        }

    }

    public void invalidate(KeyT key) {
        if (key != null) { //needed?
            cache.invalidate(getKeyRepresentation(key));
        }
    }

    public abstract KeyRepresentationT getKeyRepresentation(KeyT key);

}
