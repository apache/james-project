package org.apache.james.wkd.store;

import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;

public abstract class AbstractWebKeyDirectoryStore implements WebKeyDirectoryStore {
    
    public AbstractWebKeyDirectoryStore() {
        
    }
    
    public AbstractWebKeyDirectoryStore(
        WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager) {
        put(webKeyDirectorySubmissionAddressKeyPairManager.getPublicKeyEntryForSubmissionAddress());
    }
}
