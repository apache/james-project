package org.apache.james.mailbox;

import java.security.SecureRandom;

import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;

public class RandomModSeqProvider implements ModSeqProvider {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public ModSeq nextModSeq(Mailbox mailbox) {
        return ModSeq.of(Math.abs(secureRandom.nextLong()));
    }

    @Override
    public ModSeq nextModSeq(MailboxId mailboxId) {
        return ModSeq.of(Math.abs(secureRandom.nextLong()));
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) {
        return ModSeq.of(Math.abs(secureRandom.nextLong()));
    }

    @Override
    public ModSeq highestModSeq(MailboxId mailboxId) {
        return ModSeq.of(Math.abs(secureRandom.nextLong()));
    }
}
