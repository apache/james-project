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
 * <p>Matches if mail is from a mailing list.</p>
 * <p>Implements the match method to check if the incoming mail is from a mailing list.
 * If the mail is from a mailing list, then returns all the recipients of the mail.</p>
 */

public class IsFromMailingList extends GenericMatcher {

    /**
     * Used to detect automatically sent mails.
     */
    private final AutomaticallySentMailDetector automaticallySentMailDetector;

    /**
     * Constructor for IsFromMailingList.
     * @param automaticallySentMailDetector Mail detector.
     */
    @Inject
    public IsFromMailingList(AutomaticallySentMailDetector automaticallySentMailDetector) {
        this.automaticallySentMailDetector = automaticallySentMailDetector;
    }

    /**
     * Checks if the incoming mail is from a mailing list and returns all the recipients of the mail.
     * @param mail Mail to be matched.
     * @throws MessagingException if there is a problem while matching the mail.
     * @return Collection of MailAddress if matches, else an empty Collection.
     */
    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        if (automaticallySentMailDetector.isMailingList(mail)) {
            return mail.getRecipients();
        }
        return Collections.emptyList();
    }
}
