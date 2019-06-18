package org.apache.james.mailbox.caching;

/**
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * This module lacks tests and is not used in James products hence the choice to deprecate it.
 */
@Deprecated
public interface CacheLoaderFromUnderlying<KeyT, ValueT, UnderlyingT, ExceptT extends Throwable> {
    ValueT load(KeyT key, UnderlyingT underlying) throws ExceptT;
}
