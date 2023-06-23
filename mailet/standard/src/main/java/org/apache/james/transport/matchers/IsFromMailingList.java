package org.apache.james.transport.matchers;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.GenericMatcher;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.Collection;
import java.util.Collections;

/**
 * Matches if mail is from a mailing list.
 */
public class IsFromMailingList extends GenericMatcher {

    private final AutomaticallySentMailDetector automaticallySentMailDetector;

    @Inject
    public IsFromMailingList(AutomaticallySentMailDetector automaticallySentMailDetector) {
        this.automaticallySentMailDetector = automaticallySentMailDetector;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (automaticallySentMailDetector.isMailingList(mail)) {
            return mail.getRecipients();
        }
        return Collections.emptyList();
    }
}
