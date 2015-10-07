package org.apache.james.mailbox.caching.guava;

import org.apache.james.mailbox.caching.CacheLoaderFromUnderlying;

import com.google.common.cache.Cache;

public abstract class GuavaCacheWrapper<Key, Value, Underlying, KeyRepresentation, Except extends Throwable>
	implements CacheLoaderFromUnderlying<Key, Value, Underlying, Except> {

	private final Cache<KeyRepresentation, Value> cache;
//	private final CacheLoaderFromUnderlying<Key, Value, Underlying, Except> loader;

	public GuavaCacheWrapper(Cache<KeyRepresentation, Value> cache/*, CacheLoaderFromUnderlying<Key, Value, Underlying, Except> loader*/) {
		this.cache = cache;
//		this.loader = loader;
	}
	
	public Value get(Key key, Underlying underlying) throws Except {
		Value value = cache.getIfPresent(getKeyRepresentation(key));
		if (value != null)
			return value;
		else {
			value = load(key, underlying);
			if (value != null)
				cache.put(getKeyRepresentation(key), value);
			return value;
		}

	}
	
	public void invalidate(Key key) {
		if (key != null) //needed?
			cache.invalidate(getKeyRepresentation(key));
	}

	public abstract KeyRepresentation getKeyRepresentation(Key key);

}
