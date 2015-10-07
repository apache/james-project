package org.apache.james.mailbox.caching.guava;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;

public class AbstractGuavaCache {

	// TODO this can probably be instantiated more elegant way
	protected static final CacheBuilder<Object, Object> BUILDER = 
			CacheBuilder.newBuilder()			
			.maximumSize(100000)
			.recordStats()
			.expireAfterWrite(15, TimeUnit.MINUTES);

}
