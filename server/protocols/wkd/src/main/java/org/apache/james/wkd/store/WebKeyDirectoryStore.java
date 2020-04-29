package org.apache.james.wkd.store;

public interface WebKeyDirectoryStore {

    public void put(PublicKeyEntry bytes);

    public PublicKeyEntry get(String hash);

    public boolean containsKey(String hash);

}