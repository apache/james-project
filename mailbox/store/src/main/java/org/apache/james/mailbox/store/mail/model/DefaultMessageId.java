package org.apache.james.mailbox.store.mail.model;

import org.apache.james.mailbox.model.MessageId;

public class DefaultMessageId implements MessageId {

    public static class Factory implements MessageId.Factory {

        @Override
        public MessageId generate() {
            return new DefaultMessageId();
        }
        
    }

    @Override
    public String serialize() {
        throw new IllegalStateException("Capabilities should prevent calling this method");
    }
    
}
