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

package org.apache.james.fetchmail;

import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.StringTokenizer;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.ParseException;

import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.server.core.MailImpl;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Class <code>MessageProcessor</code> handles the delivery of
 * <code>MimeMessages</code> to the James input spool.
 * </p>
 * <p/>
 * <p>
 * Messages written to the input spool always have the following Mail Attributes
 * set:
 * </p>
 * <dl>
 * <dt>org.apache.james.fetchmail.taskName (java.lang.String)</dt>
 * <dd>The name of the fetch task that processed the message</dd>
 * <dt>org.apache.james.fetchmail.folderName (java.lang.String)</dt>
 * <dd>The name of the folder from which the message was fetched</dd>
 * </dl>
 * <p/>
 * <p>
 * Messages written to the input spool have the following Mail Attributes set if
 * the corresponding condition is satisfied:
 * <dl>
 * <dt>org.apache.james.fetchmail.isBlacklistedRecipient</dt>
 * <dd>The recipient is in the configured blacklist</dd>
 * <dt>org.apache.james.fetchmail.isMaxMessageSizeExceeded (java.lang.String)</dt>
 * <dd>The message size exceeds the configured limit. An empty message is
 * written to the input spool. The Mail Attribute value is a String representing
 * the size of the original message in bytes.</dd>
 * <dt>org.apache.james.fetchmail.isRecipientNotFound</dt>
 * <dd>The recipient could not be found. Delivery is to the configured
 * recipient. See the discussion of delivery to a sole intended recipient below.
 * </dd>
 * <dt>org.apache.james.fetchmail.isRemoteRecievedHeaderInvalid</dt>
 * <dd>The Receieved header at the index specified by parameter
 * <code>remoteReceivedHeaderIndex</code> is invalid.</dd>
 * <dt>org.apache.james.fetchmail.isRemoteRecipient</dt>
 * <dd>The recipient is on a remote host</dd>
 * <dt>org.apache.james.fetchmail.isUserUndefined</dt>
 * <dd>The recipient is on a localhost but not defined to James</dd>
 * <dt>org.apache.james.fetchmail.isDefaultSenderLocalPart</dt>
 * <dd>The local part of the sender address could not be obtained. The default
 * value has been used.</dd>
 * <dt>org.apache.james.fetchmail.isDefaultSenderDomainPart</dt>
 * <dd>The domain part of the sender address could not be obtained. The default
 * value has been used.</dd>
 * <dt>org.apache.james.fetchmail.isDefaultRemoteAddress</dt>
 * <dd>The remote address could not be determined. The default value
 * (localhost/127.0.0.1)has been used.</dd>
 * </dl>
 * <p/>
 * <p>
 * Configuration settings - see
 * <code>org.apache.james.fetchmail.ParsedConfiguration</code> - control the
 * messages that are written to the James input spool, those that are rejected
 * and what happens to messages that are rejected.
 * </p>
 * <p/>
 * <p>
 * Rejection processing is based on the following filters:
 * </p>
 * <dl>
 * <dt>RejectRemoteRecipient</dt>
 * <dd>Rejects recipients on remote hosts</dd>
 * <dt>RejectBlacklistedRecipient</dt>
 * <dd>Rejects recipients configured in a blacklist</dd>
 * <dt>RejectUserUndefined</dt>
 * <dd>Rejects recipients on local hosts who are not defined as James users</dd>
 * <dt>RejectRecipientNotFound</dt>
 * <dd>See the discussion of delivery to a sole intended recipient below</dd>
 * <dt>RejectMaxMessageSizeExceeded</dt>
 * <dd>Rejects messages whose size exceeds the configured limit</dd>
 * <dt>RejectRemoteReceievedHeaderInvalid</dt>
 * <dd>Rejects messages whose Received header is invalid.</dd>
 * </dl>
 * <p/>
 * <p>
 * Rejection processing is intentionally limited to managing the status of the
 * messages that are rejected on the server from which they were fetched. View
 * it as a simple automation of the manual processing an end-user would perform
 * through a mail client. Messages may be marked as seen or be deleted.
 * </p>
 * <p/>
 * <p>
 * Further processing can be achieved by configuring to disable rejection for
 * one or more filters. This enables Messages that would have been rejected to
 * be written to the James input spool. The conditional Mail Attributes
 * described above identify the filter states. The Matcher/Mailet chain can then
 * be used to perform any further processing required, such as notifying the
 * Postmaster and/or sender, marking the message for error processing, etc.
 * </p>
 * <p/>
 * <p>
 * Note that in the case of a message exceeding the message size limit, the
 * message that is written to the input spool has no content. This enables
 * configuration of a mailet notifying the sender that their mail has not been
 * delivered due to its size while maintaining the purpose of the filter which
 * is to avoid injecting excessively large messages into the input spool.
 * </p>
 * <p/>
 * <p>
 * Delivery is to a sole intended recipient. The recipient is determined in the
 * following manner:
 * </p>
 * <p/>
 * <ol>
 * <li>If isIgnoreIntendedRecipient(), use the configured recipient</li>
 * <li>If the Envelope contains a for: stanza, use the recipient in the stanza</li>
 * <li>If the Message has a sole intended recipient, use this recipient</li>
 * <li>If not rejectRecipientNotFound(), use the configured recipient</li>
 * </ol>
 * <p/>
 * <p>
 * If a recipient cannot be determined after these steps, the message is
 * rejected.
 * </p>
 * <p/>
 * <p>
 * Every delivered message CURRENTLY has an "X-fetched-from" header added
 * containing the name of the fetch task. Its primary uses are to detect
 * bouncing mail and provide backwards compatibility with the fetchPop task that
 * inserted this header to enable injected messages to be detected in the
 * Matcher/Mailet chain. This header is DEPRECATED and WILL BE REMOVED in a
 * future version of fetchmail. Use the Mail Attribute
 * <code>org.apache.james.fetchmail.taskName</code> instead.
 * <p/>
 * <p>
 * <code>MessageProcessor</code> is as agnostic as it can be about the format
 * and contents of the messages it delivers. There are no RFCs that govern its
 * behavior. The most releveant RFCs relate to the exchange of messages between
 * MTA servers, but not POP3 or IMAP servers which are normally end-point
 * servers and not expected to re-inject mail into MTAs. None the less, the
 * intent is to conform to the 'spirit' of the RFCs.
 * <code>MessageProcessor</code> relies on the MTA (James in this
 * implementation) to manage and validate the injected mail just as it would
 * when receiving mail from an upstream MTA.
 * </p>
 * <p/>
 * <p>
 * The only correction applied by <code>MessageProcessor</code> is to correct a
 * missing or partial sender address. If the sender address can not be obtained,
 * the default local part and default domain part is added. If the sender domain
 * part is absent, the default domain part is added.
 * </p>
 * <p/>
 * <p>
 * Mail with corrections applied to the sender address will most likely pass
 * Matcher tests on the sender that they might otherwise fail. The Mail
 * Attributes <code>org.apache.james.fetchmail.isDefaultSenderLocalPart</code>
 * and <code>org.apache.james.fetchmail.isDefaultSenderDomainPart</code> are
 * added to the injected mail to enable such mail to be detected and processed
 * accordingly.
 * </p>
 * <p/>
 * <p>
 * The status of messages on the server from which they were fetched that cannot
 * be injected into the input spool due to non-correctable errors is determined
 * by the undeliverable configuration options.
 * </p>
 */
public class MessageProcessor extends ProcessorAbstract {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);

    private MimeMessage fieldMessageIn;

    /**
     * Recipient cannot be found
     */
    private boolean fieldRecipientNotFound = false;

    /**
     * Recipient is a local user on a local host
     */
    private boolean fieldRemoteRecipient = true;

    /**
     * The mail's Received header at index remoteReceivedHeaderIndex is invalid.
     */
    private Boolean fieldRemoteReceivedHeaderInvalid;

    /**
     * Recipient is not a local user
     */
    private boolean fieldUserUndefined = false;

    /**
     * The Maximum Message has been exceeded
     */
    private Boolean fieldMaxMessageSizeExceeded;

    /**
     * Field names for an RFC2822 compliant RECEIVED Header
     */
    private static final String fieldRFC2822RECEIVEDHeaderFields = "from by via with id for ;";

    /**
     * Recipient is blacklisted
     */
    private boolean fieldBlacklistedRecipient = false;

    /**
     * The RFC2822 compliant "Received : from" domain
     */
    private String fieldRemoteDomain;

    /**
     * The remote address derived from the remote domain
     */
    private String fieldRemoteAddress;

    /**
     * The remote host name derived from the remote domain
     */
    private String fieldRemoteHostName;

    /**
     * The default sender local part has been used.
     */
    private boolean fieldDefaultSenderLocalPart = false;

    /**
     * The default sender domain part has been used.
     */
    private boolean fieldDefaultSenderDomainPart = false;

    /**
     * The default remote address has been used.
     */
    private boolean fieldDefaultRemoteAddress = false;

    /**
     * Constructor for MessageProcessor.
     *
     * @param account
     */
    private MessageProcessor(Account account) {
        super(account);
    }

    /**
     * Constructor for MessageProcessor.
     *
     * @param messageIn
     * @param account
     */

    MessageProcessor(MimeMessage messageIn, Account account) {
        this(account);
        setMessageIn(messageIn);
    }

    /**
     * Method process attempts to deliver a fetched message.
     */
    @Override
    public void process() throws MessagingException {
        // Log delivery attempt
        LOGGER.debug("Attempting delivery of message with id. {}", getMessageIn().getMessageID());

        // Determine the intended recipient
        MailAddress intendedRecipient = getIntendedRecipient();
        setRecipientNotFound(null == intendedRecipient);

        if (isRecipientNotFound()) {
            if (isDeferRecipientNotFound()) {

                String messageID = getMessageIn().getMessageID();
                if (!getDeferredRecipientNotFoundMessageIDs().contains(messageID)) {
                    getDeferredRecipientNotFoundMessageIDs().add(messageID);
                    LOGGER.debug("Deferred processing of message for which the intended recipient could not be found. Message ID: {}", messageID);
                    return;
                } else {
                    getDeferredRecipientNotFoundMessageIDs().remove(messageID);
                    LOGGER.debug("Processing deferred message for which the intended recipient could not be found. Message ID: {}", messageID);
                }
            }

            if (isRejectRecipientNotFound()) {
                rejectRecipientNotFound();
                return;
            }
            intendedRecipient = getRecipient();
            StringBuilder messageBuffer = new StringBuilder("Intended recipient not found. Using configured recipient as new envelope recipient - ");
            messageBuffer.append(intendedRecipient);
            messageBuffer.append('.');
            logStatusInfo(messageBuffer.toString());
        }

        // Set the filter states
        setBlacklistedRecipient(isBlacklistedRecipient(intendedRecipient));
        setRemoteRecipient(!isLocalServer(intendedRecipient));
        try {
            setUserUndefined(!isLocalRecipient(intendedRecipient));
        } catch (UsersRepositoryException e) {
            throw new MessagingException("Unable to access USersRepository", e);
        }

        // Apply the filters. Return if rejected
        if (isRejectBlacklisted() && isBlacklistedRecipient()) {
            rejectBlacklistedRecipient(intendedRecipient);
            return;
        }

        if (isRejectRemoteRecipient() && isRemoteRecipient()) {
            rejectRemoteRecipient(intendedRecipient);
            return;
        }

        if (isRejectUserUndefined() && isUserUndefined()) {
            rejectUserUndefined(intendedRecipient);
            return;
        }

        if (isRejectMaxMessageSizeExceeded() && isMaxMessageSizeExceeded()) {
            rejectMaxMessageSizeExceeded(getMessageIn().getSize());
            return;
        }

        if (isRejectRemoteReceivedHeaderInvalid() && isRemoteReceivedHeaderInvalid()) {
            rejectRemoteReceivedHeaderInvalid();
            return;
        }

        // Create the mail
        // If any of the mail addresses are malformed, we will get a
        // ParseException.
        // If the IP address and host name for the remote domain cannot
        // be found, we will get an UnknownHostException.
        // In both cases, we log the problem and
        // return. The message disposition is defined by the
        // <undeliverable> attributes.
        Mail mail;
        try {
            mail = createMail(createMessage(), intendedRecipient);
        } catch (ParseException ex) {
            handleParseException(ex);
            return;
        } catch (UnknownHostException ex) {
            handleUnknownHostException(ex);
            return;
        }

        addMailAttributes(mail);
        addErrorMessages(mail);

        // If this mail is bouncing move it to the ERROR repository
        if (isBouncing()) {
            handleBouncing(mail);
            return;
        }

        // OK, lets send that mail!
        sendMail(mail);
    }

    /**
     * Method rejectRemoteRecipient.
     *
     * @param recipient
     * @throws MessagingException
     */
    protected void rejectRemoteRecipient(MailAddress recipient) throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveRemoteRecipient()) {
            setMessageDeleted();
        }

        if (isMarkRemoteRecipientSeen()) {
            setMessageSeen();
        }

        StringBuilder messageBuffer = new StringBuilder("Rejected mail intended for remote recipient: ");
        messageBuffer.append(recipient);
        messageBuffer.append('.');
        logStatusInfo(messageBuffer.toString());
    }

    /**
     * Method rejectBlacklistedRecipient.
     *
     * @param recipient
     * @throws MessagingException
     */
    protected void rejectBlacklistedRecipient(MailAddress recipient) throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveBlacklisted()) {
            setMessageDeleted();
        }
        if (isMarkBlacklistedSeen()) {
            setMessageSeen();
        }

        StringBuilder messageBuffer = new StringBuilder("Rejected mail intended for blacklisted recipient: ");
        messageBuffer.append(recipient);
        messageBuffer.append('.');
        logStatusInfo(messageBuffer.toString());
    }

    /**
     * Method rejectRecipientNotFound.
     *
     * @throws MessagingException
     */
    protected void rejectRecipientNotFound() throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveRecipientNotFound()) {
            setMessageDeleted();
        }

        if (isMarkRecipientNotFoundSeen()) {
            setMessageSeen();
        }

        StringBuilder messageBuffer = new StringBuilder("Rejected mail for which a sole intended recipient could not be found.");
        messageBuffer.append(" Recipients: ");
        Address[] allRecipients = getMessageIn().getAllRecipients();
        for (Address allRecipient : allRecipients) {
            messageBuffer.append(allRecipient);
            messageBuffer.append(' ');
        }
        messageBuffer.append('.');
        logStatusInfo(messageBuffer.toString());
    }

    /**
     * Method rejectUserUndefined.
     *
     * @param recipient
     * @throws MessagingException
     */
    protected void rejectUserUndefined(MailAddress recipient) throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveUserUndefined()) {
            setMessageDeleted();
        }

        if (isMarkUserUndefinedSeen()) {
            setMessageSeen();
        }

        StringBuilder messageBuffer = new StringBuilder("Rejected mail intended for undefined user: ");
        messageBuffer.append(recipient);
        messageBuffer.append('.');
        logStatusInfo(messageBuffer.toString());
    }

    /**
     * Method rejectMaxMessageSizeExceeded.
     *
     * @param messageSize size
     * @throws MessagingException
     */
    protected void rejectMaxMessageSizeExceeded(int messageSize) throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveMaxMessageSizeExceeded()) {
            setMessageDeleted();
        }

        if (isMarkMaxMessageSizeExceededSeen()) {
            setMessageSeen();
        }

        StringBuilder messageBuffer = new StringBuilder("Rejected mail exceeding message size limit. Message size: ");
        messageBuffer.append(messageSize / 1024);
        messageBuffer.append("KB.");
        logStatusInfo(messageBuffer.toString());
    }

    /**
     * Method rejectRemoteReceivedHeaderInvalid.
     *
     * @throws MessagingException
     */
    protected void rejectRemoteReceivedHeaderInvalid() throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveRemoteReceivedHeaderInvalid()) {
            setMessageDeleted();
        }

        if (isMarkRemoteReceivedHeaderInvalidSeen()) {
            setMessageSeen();
        }

        StringBuilder messageBuffer = new StringBuilder("Rejected mail with an invalid Received: header at index ");
        messageBuffer.append(getRemoteReceivedHeaderIndex());
        messageBuffer.append(".");
        logStatusInfo(messageBuffer.toString());
    }

    /**
     * <p>
     * Method createMessage answers a new <code>MimeMessage</code> from the
     * fetched message.
     * </p>
     * <p/>
     * <p>
     * If the maximum message size is exceeded, an empty message is created,
     * else the new message is a copy of the received message.
     * </p>
     *
     * @return MimeMessage
     * @throws MessagingException
     */
    protected MimeMessage createMessage() throws MessagingException {
        // Create a new messsage from the received message
        MimeMessage messageOut;
        if (isMaxMessageSizeExceeded()) {
            messageOut = createEmptyMessage();
        } else {
            messageOut = new MimeMessage(getMessageIn());
        }

        // set the X-fetched headers
        // Note this is still required to detect bouncing mail and
        // for backwards compatibility with fetchPop
        messageOut.addHeader("X-fetched-from", getFetchTaskName());

        return messageOut;
    }

    /**
     * Method createEmptyMessage answers a new <code>MimeMessage</code> from the
     * fetched message with the message contents removed.
     *
     * @return MimeMessage
     * @throws MessagingException
     */
    protected MimeMessage createEmptyMessage() throws MessagingException {
        // Create an empty messsage
        MimeMessage messageOut = new MimeMessage(getSession());

        // Propogate the headers and subject
        Enumeration<String> headersInEnum = getMessageIn().getAllHeaderLines();
        while (headersInEnum.hasMoreElements()) {
            messageOut.addHeaderLine(headersInEnum.nextElement());
        }
        messageOut.setSubject(getMessageIn().getSubject());

        // Add empty text
        messageOut.setText("");

        // Save
        messageOut.saveChanges();

        return messageOut;
    }

    /**
     * Method createMail creates a new <code>Mail</code>.
     *
     * @param message
     * @param recipient
     * @return Mail
     * @throws MessagingException
     */
    protected Mail createMail(MimeMessage message, MailAddress recipient) throws MessagingException, UnknownHostException {
        MailImpl.Builder builder = MailImpl.builder()
            .name(MailImpl.getId())
            .sender(getSender())
            .addRecipient(recipient)
            .mimeMessage(message);

        try {
            builder.remoteAddr(getRemoteAddress());
            builder.remoteHost(getRemoteHostName());
            setDefaultRemoteAddress(false);
        } catch (UnknownHostException e) {
            // check if we should ignore this
            // See: JAMES-795
            if (!isRejectRemoteReceivedHeaderInvalid()) {
                // Ensure the builder is created with non-null remote host name and
                // address,
                // otherwise the Mailet chain may go splat!
                builder.remoteAddr("127.0.0.1");
                builder.remoteHost("localhost");
                setDefaultRemoteAddress(true);
                logStatusInfo("Remote address could not be determined. Using localhost/127.0.0.1");
            } else {
                throw e;
            }
        }

        MailImpl mail = builder.build();
        logMailCreation(mail);
        return mail;
    }

    private void logMailCreation(MailImpl mail) {
        if (LOGGER.isDebugEnabled()) {
            StringBuilder messageBuffer = new StringBuilder("Created mail with name: ");
            messageBuffer.append(mail.getName());
            messageBuffer.append(", sender: ");
            messageBuffer.append(mail.getMaybeSender());
            messageBuffer.append(", recipients: ");
            for (Object o : mail.getRecipients()) {
                messageBuffer.append(o);
                messageBuffer.append(' ');
            }
            messageBuffer.append(", remote address: ");
            messageBuffer.append(mail.getRemoteAddr());
            messageBuffer.append(", remote host name: ");
            messageBuffer.append(mail.getRemoteHost());
            messageBuffer.append('.');
            LOGGER.debug(messageBuffer.toString());
        }
    }

    /**
     * <p>
     * Method getSender answers a <code>MailAddress</code> for the sender. When
     * the sender local part and/or domain part can not be obtained from the
     * mail, default values are used. The flags 'defaultSenderLocalPart' and
     * 'defaultSenderDomainPart' are set accordingly.
     * </p>
     *
     * @return MailAddress
     * @throws MessagingException
     */
    protected MailAddress getSender() throws MessagingException {
        String from;
        InternetAddress internetAddress;

        try {
            from = ((InternetAddress) getMessageIn().getFrom()[0]).getAddress().trim();
            setDefaultSenderLocalPart(false);
        } catch (Exception ignored) {
            from = getDefaultLocalPart();
            setDefaultSenderLocalPart(true);
            StringBuilder buffer = new StringBuilder(32);
            buffer.append("Sender localpart is absent. Using default value (");
            buffer.append(getDefaultLocalPart());
            buffer.append(')');
            logStatusInfo(buffer.toString());
        }

        // Check for domain part, add default if missing
        if (from.indexOf('@') < 0) {
            StringBuilder fromBuffer = new StringBuilder(from);
            fromBuffer.append('@');
            fromBuffer.append(getDefaultDomainName());
            internetAddress = new InternetAddress(fromBuffer.toString());
            setDefaultSenderDomainPart(true);

            StringBuilder buffer = new StringBuilder(32);
            buffer.append("Sender domain is absent. Using default value (");
            buffer.append(getDefaultDomainName());
            buffer.append(')');
            logStatusInfo(buffer.toString());
        } else {
            internetAddress = new InternetAddress(from);
            setDefaultSenderDomainPart(false);
        }

        return new MailAddress(internetAddress);
    }

    /**
     * <p>
     * Method computeRemoteDomain answers a <code>String</code> that is the
     * RFC2822 compliant "Received : from" domain extracted from the message
     * being processed for the remote domain that sent the message.
     * </p>
     * <p/>
     * <p>
     * Often the remote domain is the domain that sent the message to the host
     * of the message store, the second "received" header, which has an index of
     * 1. Other times, messages may be received by a edge mail server and
     * relayed internally through one or more internal mail servers prior to
     * arriving at the message store host. In these cases the index is 1 + the
     * number of internal servers through which a mail passes.
     * </p>
     * <p>
     * The index of the header to use is specified by the configuration
     * parameter <code>RemoteReceivedHeaderIndex</code>. This is set to point to
     * the received header prior to the remote mail server, the one prior to the
     * edge mail server.
     * </p>
     * <p>
     * "received" headers are searched starting at the specified index. If a
     * domain in the "received" header is not found, successively closer
     * "received" headers are tried. If a domain is not found in this way, the
     * local machine is used as the domain. Finally, if the local domain cannot
     * be determined, the local address 127.0.0.1 is used.
     * </p>
     *
     * @return String An RFC2822 compliant "Received : from" domain name
     */
    protected String computeRemoteDomain() throws MessagingException {
        StringBuilder domainBuffer = new StringBuilder();
        String[] headers = null;

        if (getRemoteReceivedHeaderIndex() > -1) {
            headers = getMessageIn().getHeader(RFC2822Headers.RECEIVED);
        }

        // There are RECEIVED headers if the array is not null
        // and its length at is greater than 0
        boolean hasHeaders = (null != headers && headers.length > 0);

        // If there are RECEIVED headers try and extract the domain
        if (hasHeaders) {
            final String headerTokens = " \n\r";

            // Search the headers for a domain
            for (int headerIndex = headers.length > getRemoteReceivedHeaderIndex() ? getRemoteReceivedHeaderIndex() : headers.length - 1; headerIndex >= 0 && domainBuffer.length() == 0; headerIndex--) {
                // Find the "from" token
                StringTokenizer tokenizer = new StringTokenizer(headers[headerIndex], headerTokens);
                boolean inFrom = false;
                while (!inFrom && tokenizer.hasMoreTokens()) {
                    inFrom = tokenizer.nextToken().equals("from");
                }
                // Add subsequent tokens to the domain buffer until another
                // field is encountered or there are no more tokens
                while (inFrom && tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    inFrom = (!getRFC2822RECEIVEDHeaderFields().contains(token));
                    if (inFrom) {
                        domainBuffer.append(token);
                        domainBuffer.append(' ');
                    }
                }
            }
        }
        // If a domain was not found, the default is the local host and
        // if we cannot resolve this, the local address 127.0.0.1
        // Note that earlier versions of this code simply used 'localhost'
        // which works fine with java.net but is not resolved by dnsjava
        // which was introduced in v2.2.0. See Jira issue JAMES-302.
        if (domainBuffer.length() == 0) {
            try {
                domainBuffer.append(getDNSServer().getLocalHost().getCanonicalHostName());
            } catch (UnknownHostException ue) {
                domainBuffer.append("[127.0.0.1]");
            }
        }
        return domainBuffer.toString().trim();
    }

    /**
     * Method handleBouncing sets the Mail state to ERROR and delete from the
     * message store.
     *
     * @param mail
     */
    protected void handleBouncing(Mail mail) throws MessagingException {
        mail.setState(Mail.ERROR);
        setMessageDeleted();

        mail.setErrorMessage("This mail from FetchMail task " + getFetchTaskName() + " seems to be bouncing!");
        logStatusError("Message is bouncing! Deleted from message store and moved to the Error repository.");
    }

    /**
     * Method handleParseException.
     *
     * @param ex
     * @throws MessagingException
     */
    protected void handleParseException(ParseException ex) throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveUndeliverable()) {
            setMessageDeleted();
        }
        if (isMarkUndeliverableSeen()) {
            setMessageSeen();
        }
        logStatusWarn("Message could not be delivered due to an error parsing a mail address.");
        LOGGER.debug("UNDELIVERABLE Message ID: {}", getMessageIn().getMessageID(), ex);
    }

    /**
     * Method handleUnknownHostException.
     *
     * @param ex
     * @throws MessagingException
     */
    protected void handleUnknownHostException(UnknownHostException ex) throws MessagingException {
        // Update the flags of the received message
        if (!isLeaveUndeliverable()) {
            setMessageDeleted();
        }

        if (isMarkUndeliverableSeen()) {
            setMessageSeen();
        }

        logStatusWarn("Message could not be delivered due to an error determining the remote domain.");
        LOGGER.debug("UNDELIVERABLE Message ID: {}", getMessageIn().getMessageID(), ex);
    }

    /**
     * Method isLocalRecipient.
     *
     * @param recipient
     * @return boolean
     * @throws UsersRepositoryException
     */
    protected boolean isLocalRecipient(MailAddress recipient) throws UsersRepositoryException {
        // check if we use virtualhosting or not and use the right part of
        // the recipient in respect of this
        // See JAMES-1135
        return isLocalServer(recipient)
            && getLocalUsers().contains(getLocalUsers().getUsername(recipient));
    }

    /**
     * Method isLocalServer.
     *
     * @param recipient
     * @return boolean
     */
    protected boolean isLocalServer(MailAddress recipient) {
        try {
            return getConfiguration().getDomainList().containsDomain(recipient.getDomain());
        } catch (DomainListException e) {
            LOGGER.error("Unable to access DomainList", e);
            return false;
        }
    }

    /**
     * Method isBlacklistedRecipient.
     *
     * @param recipient
     * @return boolean
     */
    protected boolean isBlacklistedRecipient(MailAddress recipient) {
        return getBlacklist().contains(recipient);
    }

    /**
     * Check if this mail has been bouncing by counting the X-fetched-from
     * headers for this task
     *
     * @return boolean
     */
    protected boolean isBouncing() throws MessagingException {
        Enumeration<String> enumeration = getMessageIn().getMatchingHeaderLines(new String[]{"X-fetched-from"});
        int count = 0;
        while (enumeration.hasMoreElements()) {
            String header = enumeration.nextElement();
            if (header.equals(getFetchTaskName())) {
                count++;
            }
        }
        return count >= 3;
    }

    /**
     * Method sendMail.
     *
     * @param mail
     * @throws MessagingException
     */
    protected void sendMail(Mail mail) throws MessagingException {
        // queue the mail
        getMailQueue().enQueue(mail);

        // Update the flags of the received message
        if (!isLeave()) {
            setMessageDeleted();
        }

        if (isMarkSeen()) {
            setMessageSeen();
        }

        // Log the status
        StringBuilder messageBuffer = new StringBuilder("Spooled message to recipients: ");
        for (MailAddress address : mail.getRecipients()) {
            messageBuffer.append(address);
            messageBuffer.append(' ');
        }
        messageBuffer.append('.');
        logStatusInfo(messageBuffer.toString());
    }

    /**
     * Method getEnvelopeRecipient answers the recipient if found else null.
     * <p/>
     * Try and parse the "for" parameter from a Received header.<br>
     * Maybe not the most accurate parsing in the world but it should do.<br>
     * I opted not to use ORO (maybe I should have).
     *
     * @param msg
     * @return String
     */

    protected String getEnvelopeRecipient(MimeMessage msg) throws MessagingException {
        String res = getCustomRecipientHeader();
        if (res != null && res.length() > 0) {
            String[] headers = msg.getHeader(getCustomRecipientHeader());
            if (headers != null) {
                String mailFor = headers[0];
                if (mailFor.startsWith("<") && mailFor.endsWith(">")) {
                    mailFor = mailFor.substring(1, (mailFor.length() - 1));
                }
                return mailFor;
            }
        } else {
            try {
                Enumeration<String> enumeration = msg.getMatchingHeaderLines(new String[]{"Received"});
                while (enumeration.hasMoreElements()) {
                    String received = enumeration.nextElement();

                    int nextSearchAt = 0;
                    int i = 0;
                    int start = 0;
                    int end = 0;
                    boolean usableAddress = false;
                    while (!usableAddress && (i != -1)) {
                        i = received.indexOf("for ", nextSearchAt);
                        if (i > 0) {
                            start = i + 4;
                            end = 0;
                            nextSearchAt = start;
                            for (int c = start; c < received.length(); c++) {
                                char ch = received.charAt(c);
                                switch (ch) {
                                    case '<':
                                        continue;
                                    case '@':
                                        usableAddress = true;
                                        continue;
                                    case ' ':
                                        end = c;
                                        break;
                                    case ';':
                                        end = c;
                                        break;
                                }
                                if (end > 0) {
                                    break;
                                }
                            }
                        }
                    }
                    if (usableAddress) {
                        // lets try and grab the email address
                        String mailFor = received.substring(start, end);

                        // strip the <> around the address if there are any
                        if (mailFor.startsWith("<") && mailFor.endsWith(">")) {
                            mailFor = mailFor.substring(1, (mailFor.length() - 1));
                        }

                        return mailFor;
                    }
                }
            } catch (MessagingException me) {
                logStatusWarn("No Received headers found.");
            }
        }
        return null;
    }

    /**
     * Method getIntendedRecipient answers the sole intended recipient else
     * null.
     *
     * @return MailAddress
     * @throws MessagingException
     */
    protected MailAddress getIntendedRecipient() throws MessagingException {
        // If the original recipient should be ignored, answer the
        // hard-coded recipient
        if (isIgnoreRecipientHeader()) {
            StringBuilder messageBuffer = new StringBuilder("Ignoring recipient header. Using configured recipient as new envelope recipient: ");
            messageBuffer.append(getRecipient());
            messageBuffer.append('.');
            logStatusInfo(messageBuffer.toString());
            return getRecipient();
        }

        // If we can determine who the message was received for, answer
        // the target recipient
        String targetRecipient = getEnvelopeRecipient(getMessageIn());
        if (targetRecipient != null) {
            MailAddress recipient = new MailAddress(targetRecipient);
            StringBuilder messageBuffer = new StringBuilder("Using original envelope recipient as new envelope recipient: ");
            messageBuffer.append(recipient);
            messageBuffer.append('.');
            logStatusInfo(messageBuffer.toString());
            return recipient;
        }

        // If we can determine the intended recipient from all of the
        // recipients,
        // answer the intended recipient. This requires that there is exactly
        // one
        // recipient answered by getAllRecipients(), which examines the TO: CC:
        // and
        // BCC: headers
        Address[] allRecipients = getMessageIn().getAllRecipients();
        if (allRecipients.length == 1) {
            MailAddress recipient = new MailAddress((InternetAddress) allRecipients[0]);
            StringBuilder messageBuffer = new StringBuilder("Using sole recipient header address as new envelope recipient: ");
            messageBuffer.append(recipient);
            messageBuffer.append('.');
            logStatusInfo(messageBuffer.toString());
            return recipient;
        }

        return null;
    }

    /**
     * Returns the messageIn.
     *
     * @return MimeMessage
     */
    protected MimeMessage getMessageIn() {
        return fieldMessageIn;
    }

    /**
     * Sets the messageIn.
     *
     * @param messageIn The messageIn to set
     */
    protected void setMessageIn(MimeMessage messageIn) {
        fieldMessageIn = messageIn;
    }

    /**
     * Returns the localRecipient.
     *
     * @return boolean
     */
    protected boolean isRemoteRecipient() {
        return fieldRemoteRecipient;
    }

    /**
     * Returns <code>boolean</code> indicating if the message to be delivered
     * was unprocessed in a previous delivery attempt.
     *
     * @return boolean
     */
    protected boolean isPreviouslyUnprocessed() {
        return true;
    }

    /**
     * Log the status of the current message as INFO.
     *
     * @param detailMsg
     */
    protected void logStatusInfo(String detailMsg) throws MessagingException {
        LOGGER.info("{}", getStatusReport(detailMsg));
    }

    /**
     * Log the status the current message as WARN.
     *
     * @param detailMsg
     */
    protected void logStatusWarn(String detailMsg) throws MessagingException {
        LOGGER.warn("{}", getStatusReport(detailMsg));
    }

    /**
     * Log the status the current message as ERROR.
     *
     * @param detailMsg
     */
    protected void logStatusError(String detailMsg) throws MessagingException {
        LOGGER.error("{}", getStatusReport(detailMsg));
    }

    /**
     * Answer a <code>StringBuilder</code> containing a message reflecting the
     * current status of the message being processed.
     *
     * @param detailMsg
     * @return StringBuilder
     */
    protected StringBuilder getStatusReport(String detailMsg) throws MessagingException {
        StringBuilder messageBuffer = new StringBuilder(detailMsg);
        if (detailMsg.length() > 0) {
            messageBuffer.append(' ');
        }
        messageBuffer.append("Message ID: ");
        messageBuffer.append(getMessageIn().getMessageID());
        messageBuffer.append(". Flags: Seen = ");
        messageBuffer.append(Boolean.valueOf(isMessageSeen()));
        messageBuffer.append(", Delete = ");
        messageBuffer.append(Boolean.valueOf(isMessageDeleted()));
        messageBuffer.append('.');
        return messageBuffer;
    }

    /**
     * Returns the userUndefined.
     *
     * @return boolean
     */
    protected boolean isUserUndefined() {
        return fieldUserUndefined;
    }

    /**
     * Is the DELETED flag set?
     *
     * @throws MessagingException
     */
    protected boolean isMessageDeleted() throws MessagingException {
        return getMessageIn().isSet(Flags.Flag.DELETED);
    }

    /**
     * Is the SEEN flag set?
     *
     * @throws MessagingException
     */
    protected boolean isMessageSeen() throws MessagingException {
        return getMessageIn().isSet(Flags.Flag.SEEN);
    }

    /**
     * Set the DELETED flag.
     *
     * @throws MessagingException
     */
    protected void setMessageDeleted() throws MessagingException {
        getMessageIn().setFlag(Flags.Flag.DELETED, true);
    }

    /**
     * Set the SEEN flag.
     *
     * @throws MessagingException
     */
    protected void setMessageSeen() throws MessagingException {
        // If the Seen flag is not handled by the folder
        // allow a handler to do whatever it deems necessary
        if (!getMessageIn().getFolder().getPermanentFlags().contains(Flags.Flag.SEEN)) {
            handleMarkSeenNotPermanent();
        } else {
            getMessageIn().setFlag(Flags.Flag.SEEN, true);
        }
    }

    /**
     * <p>
     * Handler for when the folder does not support the SEEN flag. The default
     * behaviour implemented here is to log a warning and set the flag anyway.
     * </p>
     * <p/>
     * <p>
     * Subclasses may choose to override this and implement their own solutions.
     * </p>
     *
     * @throws MessagingException
     */
    protected void handleMarkSeenNotPermanent() throws MessagingException {
        getMessageIn().setFlag(Flags.Flag.SEEN, true);
        logStatusWarn("Message marked as SEEN, but the folder does not support a permanent SEEN flag.");
    }

    /**
     * Returns the Blacklisted.
     *
     * @return boolean
     */
    protected boolean isBlacklistedRecipient() {
        return fieldBlacklistedRecipient;
    }

    /**
     * Sets the localRecipient.
     *
     * @param localRecipient The localRecipient to set
     */
    protected void setRemoteRecipient(boolean localRecipient) {
        fieldRemoteRecipient = localRecipient;
    }

    /**
     * Sets the userUndefined.
     *
     * @param userUndefined The userUndefined to set
     */
    protected void setUserUndefined(boolean userUndefined) {
        fieldUserUndefined = userUndefined;
    }

    /**
     * Adds the mail attributes to a <code>Mail</code>.
     *
     * @param aMail a Mail instance
     */
    protected void addMailAttributes(Mail aMail) throws MessagingException {
        aMail.setAttribute(new Attribute(makeAttributeName("taskName"), AttributeValue.of(getFetchTaskName())));

        aMail.setAttribute(new Attribute(makeAttributeName("folderName"), AttributeValue.of(getMessageIn().getFolder().getFullName())));

        if (isRemoteRecipient()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isRemoteRecipient"), AttributeValue.of(true)));
        }

        if (isUserUndefined()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isUserUndefined"), AttributeValue.of(true)));
        }

        if (isBlacklistedRecipient()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isBlacklistedRecipient"), AttributeValue.of(true)));
        }

        if (isRecipientNotFound()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isRecipientNotFound"), AttributeValue.of(true)));
        }

        if (isMaxMessageSizeExceeded()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isMaxMessageSizeExceeded"), AttributeValue.of(Integer.toString(getMessageIn().getSize()))));
        }

        if (isRemoteReceivedHeaderInvalid()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isRemoteReceivedHeaderInvalid"), AttributeValue.of(true)));
        }

        if (isDefaultSenderLocalPart()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isDefaultSenderLocalPart"), AttributeValue.of(true)));
        }

        if (isDefaultSenderDomainPart()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isDefaultSenderDomainPart"), AttributeValue.of(true)));
        }

        if (isDefaultRemoteAddress()) {
            aMail.setAttribute(new Attribute(makeAttributeName("isDefaultRemoteAddress"), AttributeValue.of(true)));
        }
    }

    /**
     * Adds any required error messages to a <code>Mail</code>.
     *
     * @param mail a Mail instance
     */
    protected void addErrorMessages(Mail mail) throws MessagingException {
        if (isMaxMessageSizeExceeded()) {
            StringBuilder msgBuffer = new StringBuilder("550 - Rejected - This message has been rejected as the message size of ");
            msgBuffer.append(getMessageIn().getSize() * 1000 / 1024 / 1000f);
            msgBuffer.append("KB exceeds the maximum permitted size of ");
            msgBuffer.append(getMaxMessageSizeLimit() / 1024);
            msgBuffer.append("KB.");
            mail.setErrorMessage(msgBuffer.toString());
        }
    }

    /**
     * Sets the Blacklisted.
     *
     * @param blacklisted The blacklisted to set
     */
    protected void setBlacklistedRecipient(boolean blacklisted) {
        fieldBlacklistedRecipient = blacklisted;
    }

    /**
     * Returns the recipientNotFound.
     *
     * @return boolean
     */
    protected boolean isRecipientNotFound() {
        return fieldRecipientNotFound;
    }

    /**
     * Sets the recipientNotFound.
     *
     * @param recipientNotFound The recipientNotFound to set
     */
    protected void setRecipientNotFound(boolean recipientNotFound) {
        fieldRecipientNotFound = recipientNotFound;
    }

    /**
     * Returns the remoteDomain, lazily initialised as required.
     *
     * @return String
     */
    protected String getRemoteDomain() throws MessagingException {
        String remoteDomain;
        if (null == (remoteDomain = getRemoteDomainBasic())) {
            updateRemoteDomain();
            return getRemoteDomain();
        }
        return remoteDomain;
    }

    /**
     * Returns the remoteDomain.
     *
     * @return String
     */
    private String getRemoteDomainBasic() {
        return fieldRemoteDomain;
    }

    /**
     * Sets the remoteDomain.
     *
     * @param remoteDomain The remoteDomain to set
     */
    protected void setRemoteDomain(String remoteDomain) {
        fieldRemoteDomain = remoteDomain;
    }

    /**
     * Updates the remoteDomain.
     */
    protected void updateRemoteDomain() throws MessagingException {
        setRemoteDomain(computeRemoteDomain());
    }

    /**
     * Answer the IP Address of the remote server for the message being
     * processed.
     *
     * @return String
     * @throws MessagingException
     * @throws UnknownHostException
     */
    protected String computeRemoteAddress() throws MessagingException, UnknownHostException {
        String domain = getRemoteDomain();
        String address;
        String validatedAddress;
        int ipAddressStart = domain.indexOf('[');
        int ipAddressEnd = -1;

        if (ipAddressStart > -1) {
            ipAddressEnd = domain.indexOf(']', ipAddressStart);
        } else {
            // Handle () which enclose the ipaddress
            // See JAMES-344
            ipAddressStart = domain.indexOf('(');
            if (ipAddressStart > -1) {
                ipAddressEnd = domain.indexOf(')', ipAddressStart);
            }
        }
        if (ipAddressEnd > -1) {
            address = domain.substring(ipAddressStart + 1, ipAddressEnd);
        } else {
            int hostNameEnd = domain.indexOf(' ');
            if (hostNameEnd == -1) {
                hostNameEnd = domain.length();
            }
            address = domain.substring(0, hostNameEnd);
        }
        validatedAddress = getDNSServer().getByName(address).getHostAddress();

        return validatedAddress;
    }

    /**
     * Answer the Canonical host name of the remote server for the message being
     * processed.
     *
     * @return String
     * @throws MessagingException
     * @throws UnknownHostException
     */
    protected String computeRemoteHostName() throws MessagingException, UnknownHostException {
        return getDNSServer().getHostName(getDNSServer().getByName(getRemoteAddress()));
    }

    /**
     * Returns the remoteAddress, lazily initialised as required.
     *
     * @return String
     */
    protected String getRemoteAddress() throws MessagingException, UnknownHostException {
        String remoteAddress;
        if (null == (remoteAddress = getRemoteAddressBasic())) {
            updateRemoteAddress();
            return getRemoteAddress();
        }
        return remoteAddress;
    }

    /**
     * Returns the remoteAddress.
     *
     * @return String
     */
    private String getRemoteAddressBasic() {
        return fieldRemoteAddress;
    }

    /**
     * Returns the remoteHostName, lazily initialised as required.
     *
     * @return String
     */
    protected String getRemoteHostName() throws MessagingException, UnknownHostException {
        String remoteHostName;
        if (null == (remoteHostName = getRemoteHostNameBasic())) {
            updateRemoteHostName();
            return getRemoteHostName();
        }
        return remoteHostName;
    }

    /**
     * Returns the remoteHostName.
     *
     * @return String
     */
    private String getRemoteHostNameBasic() {
        return fieldRemoteHostName;
    }

    /**
     * Sets the remoteAddress.
     *
     * @param remoteAddress The remoteAddress to set
     */
    protected void setRemoteAddress(String remoteAddress) {
        fieldRemoteAddress = remoteAddress;
    }

    /**
     * Updates the remoteAddress.
     */
    protected void updateRemoteAddress() throws MessagingException, UnknownHostException {
        setRemoteAddress(computeRemoteAddress());
    }

    /**
     * Sets the remoteHostName.
     *
     * @param remoteHostName The remoteHostName to set
     */
    protected void setRemoteHostName(String remoteHostName) {
        fieldRemoteHostName = remoteHostName;
    }

    /**
     * Updates the remoteHostName.
     */
    protected void updateRemoteHostName() throws MessagingException, UnknownHostException {
        setRemoteHostName(computeRemoteHostName());
    }

    /**
     * Returns the rFC2822RECEIVEDHeaderFields.
     *
     * @return String
     */
    public static String getRFC2822RECEIVEDHeaderFields() {
        return fieldRFC2822RECEIVEDHeaderFields;
    }

    /**
     * Returns the maxMessageSizeExceeded, lazily initialised as required.
     *
     * @return Boolean
     */
    protected Boolean isMaxMessageSizeExceeded() throws MessagingException {
        Boolean isMaxMessageSizeExceeded;
        if (null == (isMaxMessageSizeExceeded = isMaxMessageSizeExceededBasic())) {
            updateMaxMessageSizeExceeded();
            return isMaxMessageSizeExceeded();
        }
        return isMaxMessageSizeExceeded;
    }

    /**
     * Refreshes the maxMessageSizeExceeded.
     */
    protected void updateMaxMessageSizeExceeded() throws MessagingException {
        setMaxMessageSizeExceeded(computeMaxMessageSizeExceeded());
    }

    /**
     * Compute the maxMessageSizeExceeded.
     *
     * @return Boolean
     */
    protected Boolean computeMaxMessageSizeExceeded() throws MessagingException {
        if (0 == getMaxMessageSizeLimit()) {
            return Boolean.FALSE;
        }
        return getMessageIn().getSize() > getMaxMessageSizeLimit();
    }

    /**
     * Returns the maxMessageSizeExceeded.
     *
     * @return Boolean
     */
    private Boolean isMaxMessageSizeExceededBasic() {
        return fieldMaxMessageSizeExceeded;
    }

    /**
     * Sets the maxMessageSizeExceeded.
     *
     * @param maxMessageSizeExceeded The maxMessageSizeExceeded to set
     */
    protected void setMaxMessageSizeExceeded(Boolean maxMessageSizeExceeded) {
        fieldMaxMessageSizeExceeded = maxMessageSizeExceeded;
    }

    /**
     * Returns the remoteReceivedHeaderInvalid, lazily initialised.
     *
     * @return Boolean
     */
    protected Boolean isRemoteReceivedHeaderInvalid() throws MessagingException {
        Boolean isInvalid;
        if (null == (isInvalid = isRemoteReceivedHeaderInvalidBasic())) {
            updateRemoteReceivedHeaderInvalid();
            return isRemoteReceivedHeaderInvalid();
        }
        return isInvalid;
    }

    /**
     * Computes the remoteReceivedHeaderInvalid.
     *
     * @return Boolean
     */
    protected Boolean computeRemoteReceivedHeaderInvalid() throws MessagingException {
        Boolean isInvalid = Boolean.FALSE;
        try {
            getRemoteAddress();
        } catch (UnknownHostException e) {
            isInvalid = Boolean.TRUE;
        }
        return isInvalid;
    }

    /**
     * Returns the remoteReceivedHeaderInvalid.
     *
     * @return Boolean
     */
    private Boolean isRemoteReceivedHeaderInvalidBasic() {
        return fieldRemoteReceivedHeaderInvalid;
    }

    /**
     * Sets the remoteReceivedHeaderInvalid.
     *
     * @param remoteReceivedHeaderInvalid The remoteReceivedHeaderInvalid to set
     */
    protected void setRemoteReceivedHeaderInvalid(Boolean remoteReceivedHeaderInvalid) {
        fieldRemoteReceivedHeaderInvalid = remoteReceivedHeaderInvalid;
    }

    /**
     * Updates the remoteReceivedHeaderInvalid.
     */
    protected void updateRemoteReceivedHeaderInvalid() throws MessagingException {
        setRemoteReceivedHeaderInvalid(computeRemoteReceivedHeaderInvalid());
    }

    /**
     * Returns the defaultSenderDomainPart.
     *
     * @return boolean
     */
    protected boolean isDefaultSenderDomainPart() {
        return fieldDefaultSenderDomainPart;
    }

    /**
     * Returns the defaultSenderLocalPart.
     *
     * @return boolean
     */
    protected boolean isDefaultSenderLocalPart() {
        return fieldDefaultSenderLocalPart;
    }

    /**
     * Sets the defaultSenderDomainPart.
     *
     * @param defaultSenderDomainPart The defaultSenderDomainPart to set
     */
    protected void setDefaultSenderDomainPart(boolean defaultSenderDomainPart) {
        fieldDefaultSenderDomainPart = defaultSenderDomainPart;
    }

    /**
     * Sets the defaultSenderLocalPart.
     *
     * @param defaultSenderLocalPart The defaultSenderLocalPart to set
     */
    protected void setDefaultSenderLocalPart(boolean defaultSenderLocalPart) {
        fieldDefaultSenderLocalPart = defaultSenderLocalPart;
    }

    /**
     * Returns the defaultRemoteAddress.
     *
     * @return boolean
     */
    protected boolean isDefaultRemoteAddress() {
        return fieldDefaultRemoteAddress;
    }

    /**
     * Sets the defaultRemoteAddress.
     *
     * @param defaultRemoteAddress The defaultRemoteAddress to set
     */
    protected void setDefaultRemoteAddress(boolean defaultRemoteAddress) {
        fieldDefaultRemoteAddress = defaultRemoteAddress;
    }

    private AttributeName makeAttributeName(String suffix) {
        return AttributeName.of(getAttributePrefix() + suffix);
    }
}
