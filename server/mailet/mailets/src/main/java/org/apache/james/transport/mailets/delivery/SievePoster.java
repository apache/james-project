package org.apache.james.transport.mailets.delivery;

import java.util.Date;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MimeMessageInputStream;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.transport.mailets.jsieve.Poster;
import org.apache.james.transport.util.MailetContextLog;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.MailetContext;

public class SievePoster implements Poster {

    private final MailboxManager mailboxManager;
    private final String folder;
    private final UsersRepository usersRepos;
    private final MailetContext mailetContext;

    public SievePoster(MailboxManager mailboxManager, String folder, UsersRepository usersRepos, MailetContext mailetContext) {
        this.mailboxManager = mailboxManager;
        this.folder = folder;
        this.usersRepos = usersRepos;
        this.mailetContext = mailetContext;
    }

    @Override
    public void post(String url, MimeMessage mail) throws MessagingException {

        final int endOfScheme = url.indexOf(':');

        if (endOfScheme < 0) {
            throw new MessagingException("Malformed URI");
        }

        else {

            final String scheme = url.substring(0, endOfScheme);
            if ("mailbox".equals(scheme)) {
                int startOfUser = endOfScheme + 3;
                int endOfUser = url.indexOf('@', startOfUser);
                if (endOfUser < 0) {
                    // TODO: When user missing, append to a default location
                    throw new MessagingException("Shared mailbox is not supported");
                } else {
                    // lowerCase the user - see
                    // https://issues.apache.org/jira/browse/JAMES-1369
                    String user = url.substring(startOfUser, endOfUser).toLowerCase();
                    int startOfHost = endOfUser + 1;
                    int endOfHost = url.indexOf('/', startOfHost);
                    String host = url.substring(startOfHost, endOfHost);
                    String urlPath;
                    int length = url.length();
                    if (endOfHost + 1 == length) {
                        urlPath = this.folder;
                    } else {
                        urlPath = url.substring(endOfHost, length);
                    }

                    // Check if we should use the full email address as username
                    try {
                        if (usersRepos.supportVirtualHosting()) {
                            user = user + "@" + host;
                        }
                    } catch (UsersRepositoryException e) {
                        throw new MessagingException("Unable to accessUsersRepository", e);
                    }

                    MailboxSession session;
                    try {
                        session = mailboxManager.createSystemSession(user, new MailetContextLog(mailetContext));
                    } catch (BadCredentialsException e) {
                        throw new MessagingException("Unable to authenticate to mailbox", e);
                    } catch (MailboxException e) {
                        throw new MessagingException("Can not access mailbox", e);
                    }

                    // Start processing request
                    mailboxManager.startProcessingRequest(session);

                    // This allows Sieve scripts to use a standard delimiter
                    // regardless of mailbox implementation
                    String destination = urlPath.replace('/', session.getPathDelimiter());

                    if (destination == null || "".equals(destination)) {
                        destination = this.folder;
                    }
                    if (destination.startsWith(session.getPathDelimiter() + ""))
                        destination = destination.substring(1);

                    // Use the MailboxSession to construct the MailboxPath - See
                    // JAMES-1326
                    final MailboxPath path = new MailboxPath(MailboxConstants.USER_NAMESPACE, user, destination);
                    try {
                        if (this.folder.equalsIgnoreCase(destination) && !(mailboxManager.mailboxExists(path, session))) {
                            mailboxManager.createMailbox(path, session);
                        }
                        final MessageManager mailbox = mailboxManager.getMailbox(path, session);
                        if (mailbox == null) {
                            final String error = "Mailbox for user " + user + " was not found on this server.";
                            throw new MessagingException(error);
                        }

                        mailbox.appendMessage(new MimeMessageInputStream(mail), new Date(), session, true, null);

                    } catch (MailboxException e) {
                        throw new MessagingException("Unable to access mailbox.", e);
                    } finally {
                        session.close();
                        try {
                            mailboxManager.logout(session, true);
                        } catch (MailboxException e) {
                            throw new MessagingException("Can logout from mailbox", e);
                        }

                        // Stop processing request
                        mailboxManager.endProcessingRequest(session);

                    }
                }

            }

            else {
                // TODO: add support for more protocols
                // TODO: - for example mailto: for forwarding over SMTP
                // TODO: - for example xmpp: for forwarding over Jabber
                throw new MessagingException("Unsupported protocol");
            }
        }
    }
}
