/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.smtpserver;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.mime4j.field.address.DefaultAddressParser;
import org.apache.james.mime4j.field.address.ParseException;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.SaslAuthResultHook;
import org.apache.james.server.core.InternetHeadersInputStream;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional hook rejecting messages of authenticated sessions whose <code>Sender</code> header field does not
 * disclose the logged in user.
 *
 * <p>As <code>Sender</code> names whoever actually transmits the message (RFC 5322 section 3.6.2), this check is
 * stricter than the <code>verifyIdentity</code> one applied to <code>MAIL FROM</code> and <code>From</code>: aliases
 * and other identities the user can send from do not count. Each <code>Sender</code> field must hold exactly one
 * mailbox of a local domain resolving, through {@link UsersRepository#getUsername(MailAddress)}, to the logged in
 * user.</p>
 *
 * <p>The logged in user is the SASL authentication identity: upon delegation (SASL authorization identity) it is
 * the user who authenticated, not the one the session acts as. Messages without <code>Sender</code>,
 * unauthenticated sessions and accounts allowed to use other identities are not checked.</p>
 *
 * <pre>
 * &lt;handler class="org.apache.james.smtpserver.SenderHeaderVerificationHook"/&gt;
 * </pre>
 */
public class SenderHeaderVerificationHook implements JamesMessageHook, SaslAuthResultHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SenderHeaderVerificationHook.class);
    private static final ProtocolSession.AttachmentKey<Username> LOGGED_IN_USER =
        ProtocolSession.AttachmentKey.of("SENDER_HEADER_VERIFICATION_LOGGED_IN_USER", Username.class);
    private static final String SENDER = "Sender";
    private static final HookResult SENDER_MISMATCH = HookResult.builder()
        .hookReturnCode(HookReturnCode.deny())
        .smtpReturnCode(SMTPRetCode.MAILBOX_PERM_UNAVAILABLE)
        .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH)
            + " Sender header must match the authenticated user")
        .build();
    private static final HookResult TEMPORARY_FAILURE = HookResult.builder()
        .hookReturnCode(HookReturnCode.denySoft())
        .smtpReturnCode(SMTPRetCode.LOCAL_ERROR)
        .smtpDescription(DSNStatus.getStatus(DSNStatus.TRANSIENT, DSNStatus.SYSTEM_OTHER)
            + " Temporary error while verifying the Sender header")
        .build();

    private final UsersRepository usersRepository;
    private final DomainList domainList;

    @Inject
    public SenderHeaderVerificationHook(UsersRepository usersRepository, DomainList domainList) {
        this.usersRepository = usersRepository;
        this.domainList = domainList;
    }

    @Override
    public void onSuccess(SMTPSession session, String mechanismName, SaslIdentity identity) {
        session.setAttachment(LOGGED_IN_USER, identity.authenticationId(), ProtocolSession.State.Connection);
    }

    @Override
    public void onFailure(SMTPSession session, String mechanismName, SaslFailure failure) {
    }

    @Override
    public HookResult onMessage(SMTPSession session, Mail mail) {
        if (session.getUsername() == null || session.allowUseOtherIdentity()) {
            return HookResult.DECLINED;
        }
        Username loggedInUser = session.getAttachment(LOGGED_IN_USER, ProtocolSession.State.Connection)
            .orElse(session.getUsername());
        try {
            String[] senderFields = mail.getMessage().getHeader(SENDER);
            if (senderFields == null) {
                return HookResult.DECLINED;
            }
            if (headerBlockHoldsABareCarriageReturn(mail.getMessage())) {
                // Jakarta Mail ends a header line at a bare CR, mime4j (used to display the message) does not: the
                // Sender field checked here could differ from the one shown to recipients
                LOGGER.warn("{} tried to send a mail with a bare CR in its header block alongside a Sender header", loggedInUser.asString());
                return SENDER_MISMATCH;
            }
            for (String senderField : senderFields) {
                if (!disclosesOnly(senderField, loggedInUser)) {
                    LOGGER.warn("{} tried to send a mail with Sender header {}", loggedInUser.asString(), MimeUtility.unfold(senderField));
                    return SENDER_MISMATCH;
                }
            }
            return HookResult.DECLINED;
        } catch (MessagingException | IOException | UsersRepositoryException | DomainListException e) {
            LOGGER.error("Could not verify the Sender header of a mail sent by {}", loggedInUser.asString(), e);
            return TEMPORARY_FAILURE;
        }
    }

    /**
     * Reads the header block as it will be transmitted and stored: the raw bytes received as long as the message is
     * not modified. A CR is only allowed as part of a CRLF (RFC 5322 section 2.2).
     */
    private static boolean headerBlockHoldsABareCarriageReturn(MimeMessage message) throws MessagingException, IOException {
        try (InputStream in = new BufferedInputStream(rawStream(message))) {
            boolean emptyLine = true;
            int current = in.read();
            while (current != -1) {
                if (current == '\r') {
                    if (in.read() != '\n') {
                        return true;
                    }
                    current = '\n';
                }
                if (current == '\n') {
                    if (emptyLine) {
                        // End of the header block
                        return false;
                    }
                    emptyLine = true;
                } else {
                    emptyLine = false;
                }
                current = in.read();
            }
            return false;
        }
    }

    private static InputStream rawStream(MimeMessage message) throws MessagingException {
        if (message instanceof MimeMessageWrapper wrapper) {
            return wrapper.getMessageInputStream();
        }
        return new InternetHeadersInputStream(message.getAllHeaderLines());
    }

    private boolean disclosesOnly(String senderField, Username loggedInUser) throws UsersRepositoryException, DomainListException {
        try {
            String field = MimeUtility.unfold(senderField);
            // Strict: the lenient parser turns eg 'To: bob@domain.tld;' into a plain address
            InternetAddress[] addresses = InternetAddress.parseHeader(field, true);
            if (addresses.length != 1 || addresses[0].isGroup() || !readsTheSameMailbox(field, addresses[0])) {
                return false;
            }
            MailAddress sender = new MailAddress(addresses[0].getAddress());
            return domainList.containsDomain(sender.getDomain())
                && usersRepository.getUsername(sender).equals(loggedInUser);
        } catch (AddressException | IllegalArgumentException e) {
            // IllegalArgumentException: eg a quoted local part holding a '@' cannot be turned into a username
            return false;
        }
    }

    // InternetAddress.parseHeader ignores what follows the first mailbox, eg '<bob@domain.tld> alice@domain.tld', and
    // stops at comments, eg 'bob@domain.tld(comment).evil' reads as bob@domain.tld: mime4j must read the whole field
    // as the same single mailbox
    private boolean readsTheSameMailbox(String field, InternetAddress address) {
        try {
            return DefaultAddressParser.DEFAULT.parseMailbox(field).getAddress().equals(address.getAddress());
        } catch (ParseException e) {
            return false;
        } catch (Error e) {
            // mime4j 0.8.15 wraps in an Error the IOException raised when strictly decoding a malformed encoded word
            // of the display name, eg '=?utf-8?B?*?= <bob@domain.tld>'. Later versions throw IllegalArgumentException.
            if (e.getCause() instanceof IOException) {
                return false;
            }
            throw e;
        }
    }
}
