package org.apache.james.imap;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;

public class ImapSuite {
    private final ImapDecoder decoder;
    private final ImapEncoder encoder;
    private final ImapProcessor processor;

    public ImapSuite(ImapDecoder decoder, ImapEncoder encoder, ImapProcessor processor) {
        this.decoder = decoder;
        this.encoder = encoder;
        this.processor = processor;
    }

    public ImapDecoder getDecoder() {
        return decoder;
    }

    public ImapEncoder getEncoder() {
        return encoder;
    }

    public ImapProcessor getProcessor() {
        return processor;
    }
}
