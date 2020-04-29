package org.apache.james.wkd.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;

public class MemoryWebKeyDirectoryStore extends AbstractWebKeyDirectoryStore {

    private boolean insertSubmissionKey = false;
    private Map<String, PublicKeyEntry> map = new ConcurrentHashMap<String, PublicKeyEntry>();
    private WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager;

    @Inject
    public MemoryWebKeyDirectoryStore(
        WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager) {
        this.webKeyDirectorySubmissionAddressKeyPairManager = webKeyDirectorySubmissionAddressKeyPairManager;
    }

    @Override
    public void put(PublicKeyEntry publicKeyEntry) {
        map.put(publicKeyEntry.getHash(), publicKeyEntry);
    }

    @Override
    public PublicKeyEntry get(String hash) {
        checkSubmissionKey();
        return map.get(hash);
    }

    /**
     * 
     */
    private synchronized void checkSubmissionKey() {
        if (!insertSubmissionKey) {
            put(webKeyDirectorySubmissionAddressKeyPairManager
                .getPublicKeyEntryForSubmissionAddress());
            insertSubmissionKey = true;
        }
    }

    @Override
    public boolean containsKey(String hash) {
        checkSubmissionKey();
        return map.containsKey(hash);
    }

}
