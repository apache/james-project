package org.apache.james.mailbox.caching;

public interface CacheLoaderFromUnderlying<Key, Value, Underlying, Except extends Throwable> {
	Value load(Key key, Underlying underlying) throws Except;
}
