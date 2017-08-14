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

package org.apache.james.transport.mailets.remoteDelivery;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.internet.InternetAddress;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@SuppressWarnings("deprecation")
public class MailDelivrer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailDelivrer.class);

    private final RemoteDeliveryConfiguration configuration;
    private final MailDelivrerToHost mailDelivrerToHost;
    private final DnsHelper dnsHelper;
    private final MessageComposer messageComposer;
    private final Bouncer bouncer;

    public MailDelivrer(RemoteDeliveryConfiguration configuration, MailDelivrerToHost mailDelivrerToHost, DNSService dnsServer, Bouncer bouncer) {
        this(configuration, mailDelivrerToHost, new DnsHelper(dnsServer, configuration), bouncer);
    }

    @VisibleForTesting
    MailDelivrer(RemoteDeliveryConfiguration configuration, MailDelivrerToHost mailDelivrerToHost, DnsHelper dnsHelper, Bouncer bouncer) {
        this.configuration = configuration;
        this.mailDelivrerToHost = mailDelivrerToHost;
        this.dnsHelper = dnsHelper;
        this.messageComposer = new MessageComposer(configuration);
        this.bouncer = bouncer;
    }

    /**
     * We can assume that the recipients of this message are all going to the same mail server. We will now rely on the
     * DNS server to do DNS MX record lookup and try to deliver to the multiple mail servers. If it fails, it should
     * throw an exception.
     *
     * @param mail    org.apache.james.core.MailImpl
     * @return boolean Whether the delivery was successful and the message can be deleted
     */
    public ExecutionResult deliver(Mail mail) {
        try {
            return tryDeliver(mail);
        } catch (SendFailedException sfe) {
            return handleSenderFailedException(mail, sfe);
        } catch (MessagingException ex) {
            // We check whether this is a 5xx error message, which indicates a permanent failure (like account doesn't exist
            // or mailbox is full or domain is setup wrong). We fail permanently if this was a 5xx error
            boolean isPermanent = new EnhancedMessagingException(ex).isServerError();
            return logAndReturn(mail, ExecutionResult.onFailure(isPermanent, ex));
        } catch (Exception ex) {
            LOGGER.error("Generic exception = permanent failure: {}", ex.getMessage(), ex);
            return logAndReturn(mail, ExecutionResult.permanentFailure(ex));
        }
    }

    private ExecutionResult tryDeliver(Mail mail) throws MessagingException {
        if (mail.getRecipients().isEmpty()) {
            LOGGER.info("No recipients specified... not sure how this could have happened.");
            return ExecutionResult.permanentFailure(new Exception("No recipients specified for " + mail.getName() + " sent by " + mail.getSender()));
        }
        if (configuration.isDebug()) {
            LOGGER.debug("Attempting to deliver {}", mail.getName());
        }

        String host = retrieveTargetHostname(mail);
        try {
            // Figure out which servers to try to send to. This collection
            // will hold all the possible target servers
            Iterator<HostAddress> targetServers = dnsHelper.retrieveHostAddressIterator(host);
            if (!targetServers.hasNext()) {
                return handleNoTargetServer(mail, host);
            }
            return doDeliver(mail, InternetAddressConverter.convert(mail.getRecipients()), targetServers);
        } catch (TemporaryResolutionException e) {
            return logAndReturn(mail, ExecutionResult.temporaryFailure(new MessagingException("Temporary problem looking " +
                "up mail server for host: " + host + ".  I cannot determine where to send this message.")));
        }
    }

    private String retrieveTargetHostname(Mail mail) {
        Preconditions.checkArgument(!mail.getRecipients().isEmpty(), "Mail should have recipients to attempt delivery");
        MailAddress rcpt = Iterables.getFirst(mail.getRecipients(), null);
        return rcpt.getDomain();
    }

    private ExecutionResult doDeliver(Mail mail, InternetAddress[] addr, Iterator<HostAddress> targetServers) throws MessagingException {
        MessagingException lastError = null;

        while (targetServers.hasNext()) {
            try {
                return mailDelivrerToHost.tryDeliveryToHost(mail, addr, targetServers.next());
            } catch (SendFailedException sfe) {
                lastError = handleSendFailExceptionOnMxIteration(mail, sfe);
            } catch (MessagingException me) {
                lastError = handleMessagingException(mail, me);
                if (configuration.isDebug()) {
                    LOGGER.debug(me.getMessage(), me.getCause());
                } else {
                    LOGGER.info(me.getMessage());
                }
            }
        }
        // If we encountered an exception while looping through,
        // throw the last MessagingException we caught. We only
        // do this if we were unable to send the message to any
        // server. If sending eventually succeeded, we exit
        // deliver() though the return at the end of the try
        // block.
        if (lastError != null) {
            throw lastError;
        }
        return ExecutionResult.temporaryFailure();
    }

    private MessagingException handleMessagingException(Mail mail, MessagingException me) throws MessagingException {
        LOGGER.debug("Exception delivering message ({}) - {}", mail.getName(), me.getMessage());
        if ((me.getNextException() != null) && (me.getNextException() instanceof IOException)) {
            // If it's an IO exception with no nested exception, it's probably
            // some socket or weird I/O related problem.
            return me;
        } else {
            // This was not a connection or I/O error particular to one SMTP server of an MX set. Instead, it is almost
            // certainly a protocol level error. In this case we assume that this is an error we'd encounter with any of
            // the SMTP servers associated with this MX record, and we pass the exception to the code in the outer block
            // that determines its severity.
            throw me;
        }
    }

    @VisibleForTesting
    ExecutionResult handleSenderFailedException(Mail mail, SendFailedException sfe) {
        logSendFailedException(sfe);
        EnhancedMessagingException enhancedMessagingException = new EnhancedMessagingException(sfe);
        List<MailAddress> invalidAddresses = AddressesArrayToMailAddressListConverter.getAddressesAsMailAddress(sfe.getInvalidAddresses());
        List<MailAddress> validUnsentAddresses = AddressesArrayToMailAddressListConverter.getAddressesAsMailAddress(sfe.getValidUnsentAddresses());
        if (configuration.isDebug()) {
            LOGGER.debug("Mail {} has initially recipients: {}", mail.getName(), mail.getRecipients());
            if (!invalidAddresses.isEmpty()) {
                LOGGER.debug("Invalid recipients: {}", invalidAddresses);
            }
            if (!validUnsentAddresses.isEmpty()) {
                LOGGER.debug("Unsent recipients: {}", validUnsentAddresses);
            }
        }
        if (!validUnsentAddresses.isEmpty()) {
            if (!invalidAddresses.isEmpty()) {
                mail.setRecipients(invalidAddresses);
                bouncer.bounce(mail, sfe);
            }
            mail.setRecipients(validUnsentAddresses);
            if (enhancedMessagingException.hasReturnCode()) {
                boolean isPermanent = enhancedMessagingException.isServerError();
                return logAndReturn(mail, ExecutionResult.onFailure(isPermanent, sfe));
            } else {
                return logAndReturn(mail, ExecutionResult.temporaryFailure(sfe));
            }
        }
        if (!invalidAddresses.isEmpty()) {
            mail.setRecipients(invalidAddresses);
            return logAndReturn(mail, ExecutionResult.permanentFailure(sfe));
        }

        if (enhancedMessagingException.hasReturnCode() || enhancedMessagingException.hasNestedReturnCode()) {
            if (enhancedMessagingException.isServerError()) {
                return ExecutionResult.permanentFailure(sfe);
            }
        }
        return ExecutionResult.temporaryFailure(sfe);
    }

    private ExecutionResult logAndReturn(Mail mail, ExecutionResult executionResult) {
        LOGGER.debug(messageComposer.composeFailLogMessage(mail, executionResult));
        return executionResult;
    }

    private MessagingException handleSendFailExceptionOnMxIteration(Mail mail, SendFailedException sfe) throws SendFailedException {
        logSendFailedException(sfe);

        if (sfe.getValidSentAddresses() != null) {
            Address[] validSent = sfe.getValidSentAddresses();
            if (validSent.length > 0) {
                LOGGER.debug( "Mail ({}) sent successfully for {}", mail.getName(), Arrays.asList(validSent));
            }
        }

        EnhancedMessagingException enhancedMessagingException = new EnhancedMessagingException(sfe);
        if (enhancedMessagingException.isServerError()) {
            throw sfe;
        }

        if (sfe.getValidUnsentAddresses() != null && sfe.getValidUnsentAddresses().length > 0) {
            if (configuration.isDebug())
                LOGGER.debug("Send failed, {} valid addresses remain, continuing with any other servers", ImmutableList.copyOf(sfe.getValidUnsentAddresses()));
            return sfe;
        } else {
            // There are no valid addresses left to send, so rethrow
            throw sfe;
        }
    }

    private ExecutionResult handleNoTargetServer(Mail mail, String host) {
        LOGGER.info("No mail server found for: {}", host);
        MessagingException messagingException = new MessagingException("There are no DNS entries for the hostname " + host + ".  I cannot determine where to send this message.");
        int retry = DeliveryRetriesHelper.retrieveRetries(mail);
        if (retry >= configuration.getDnsProblemRetry()) {
            return logAndReturn(mail, ExecutionResult.permanentFailure(messagingException));
        } else {
            return logAndReturn(mail, ExecutionResult.temporaryFailure(messagingException));
        }
    }

    private void logSendFailedException(SendFailedException sfe) {
        if (configuration.isDebug()) {
            EnhancedMessagingException enhancedMessagingException = new EnhancedMessagingException(sfe);
            if (enhancedMessagingException.hasReturnCode()) {
                LOGGER.info("SMTP SEND FAILED: Command [{}] RetCode: [{}] Response[{}]", enhancedMessagingException.computeCommand(),
                    enhancedMessagingException.getReturnCode(), sfe.getMessage());
            } else {
                LOGGER.info("Send failed", sfe);
            }
            logLevels(sfe);
        }
    }

    private void logLevels(MessagingException me) {
        Exception ne;
        while ((ne = me.getNextException()) != null && ne instanceof MessagingException) {
            me = (MessagingException) ne;
            EnhancedMessagingException enhancedMessagingException = new EnhancedMessagingException(me);
            if (me.getClass().getName().endsWith(".SMTPAddressFailedException") || me.getClass().getName().endsWith(".SMTPAddressSucceededException")) {
                LOGGER.debug("ADDRESS :[{}] Address:[{}] Command : [{}] RetCode[{}] Response [{}]",
                    enhancedMessagingException.computeAction(), me.toString(), enhancedMessagingException.computeAddress(),
                    enhancedMessagingException.computeCommand(), enhancedMessagingException.getReturnCode());
            }
        }
    }
}
