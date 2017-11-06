package org.apache.james.mailbox.store.mail.model;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.model.MessageId;

public class DefaultMessageId implements MessageId {

    public static class Factory implements MessageId.Factory {
        
        @Override
        public MessageId fromString(String serialized) {
            throw new NotImplementedException("MessageId is not supported by this backend");
        }
        
        @Override
        public MessageId generate() {
            return new DefaultMessageId();
        }
    }
    
    public DefaultMessageId() {
    }

    @Override
    public String serialize() {
        throw new IllegalStateException("Capabilities should prevent calling this method");
    }
    
    @Override
    public final boolean equals(Object obj) {
        throw new IllegalStateException("Capabilities should prevent calling this method");
    }
    
    @Override
    public final int hashCode() {
        throw new IllegalStateException("Capabilities should prevent calling this method");
    }
}
