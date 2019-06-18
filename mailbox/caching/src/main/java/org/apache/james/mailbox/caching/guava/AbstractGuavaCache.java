package org.apache.james.mailbox.caching.guava;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;

/**
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release, unless it finds a maintainer
 *
 * This module lacks tests and is not used in James products hence the choice to deprecate it.
 */
@Deprecated
public class AbstractGuavaCache {

    // TODO this can probably be instantiated more elegant way
    protected static final CacheBuilder<Object, Object> BUILDER =
            CacheBuilder.newBuilder()
            .maximumSize(100000)
            .recordStats()
            .expireAfterWrite(15, TimeUnit.MINUTES);

}
