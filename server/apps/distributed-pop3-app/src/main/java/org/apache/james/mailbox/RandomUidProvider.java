package org.apache.james.mailbox;

import java.security.SecureRandom;
import java.util.Optional;

import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;

public class RandomUidProvider implements UidProvider {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public MessageUid nextUid(Mailbox mailbox) {
        return MessageUid.of(Math.abs(secureRandom.nextLong()));
    }

    @Override
    public Optional<MessageUid> lastUid(Mailbox mailbox) {
        return Optional.of(MessageUid.of(Math.abs(secureRandom.nextLong())));
    }

    @Override
    public MessageUid nextUid(MailboxId mailboxId) {
        return MessageUid.of(Math.abs(secureRandom.nextLong()));
    }
}
