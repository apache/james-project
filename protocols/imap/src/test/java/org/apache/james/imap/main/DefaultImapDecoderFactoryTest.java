package org.apache.james.imap.main;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.junit.jupiter.api.Test;

class DefaultImapDecoderFactoryTest {

    @Test
    void createDefaultImapDecoderFactoryWithDefaultConstructor() {
        // Create an instance using the default constructor
        DefaultImapDecoderFactory factory = new DefaultImapDecoderFactory();

        // Assert that the factory is not null
        assertNotNull(factory, "Factory instance should not be null");

        // Build an ImapDecoder using the factory and assert it's not null
        ImapDecoder decoder = factory.buildImapDecoder();
        assertNotNull(decoder, "ImapDecoder instance should not be null");
    }

    @Test
    void createDefaultImapDecoderFactoryWithCustomStatusResponseFactory() {
        // Create a custom StatusResponseFactory
        StatusResponseFactory customStatusResponseFactory = new UnpooledStatusResponseFactory();

        // Create an instance using the custom StatusResponseFactory
        DefaultImapDecoderFactory factory = new DefaultImapDecoderFactory(customStatusResponseFactory);

        // Assert that the factory is not null
        assertNotNull(factory, "Factory instance should not be null");

        // Build an ImapDecoder using the factory and assert it's not null
        ImapDecoder decoder = factory.buildImapDecoder();
        assertNotNull(decoder, "ImapDecoder instance should not be null");
    }
}
