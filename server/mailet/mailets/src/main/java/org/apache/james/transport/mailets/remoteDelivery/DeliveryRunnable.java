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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.dnsservice.library.MXHostAddressIterator;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.metrics.api.Metric;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;

@SuppressWarnings("deprecation")
public class DeliveryRunnable implements Runnable {

    private final MailQueue queue;
    private final RemoteDeliveryConfiguration configuration;
    private final DNSService dnsServer;
    private final Metric outgoingMailsMetric;
    private final Logger logger;
    private final Bouncer bouncer;
    private final MailDelivrerToHost mailDelivrerToHost;
    private final VolatileIsDestroyed volatileIsDestroyed;
    private final MessageComposer messageComposer;

    public DeliveryRunnable(MailQueue queue, RemoteDeliveryConfiguration configuration, DNSService dnsServer, Metric outgoingMailsMetric,
                            Logger logger, MailetContext mailetContext, VolatileIsDestroyed volatileIsDestroyed) {
        this.queue = queue;
        this.configuration = configuration;
        this.dnsServer = dnsServer;
        this.outgoingMailsMetric = outgoingMailsMetric;
        this.logger = logger;
        this.volatileIsDestroyed = volatileIsDestroyed;
        this.messageComposer = new MessageComposer(configuration);
        this.bouncer = new Bouncer(configuration, messageComposer, mailetContext, logger);
        this.mailDelivrerToHost = new MailDelivrerToHost(configuration, mailetContext, logger);
    }

    /**
     * Handles checking the outgoing spool for new mail and delivering them if
     * there are any
     */
    @Override
    public void run() {
        try {
            while (!Thread.interrupted() && !volatileIsDestroyed.isDestroyed()) {
                try {
                    // Get the 'mail' object that is ready for deliverying. If no message is
                    // ready, the 'accept' will block until message is ready.
                    // The amount of time to block is determined by the 'getWaitTime' method of the MultipleDelayFilter.
                    MailQueue.MailQueueItem queueItem = queue.deQueue();
                    Mail mail = queueItem.getMail();

                    try {
                        if (configuration.isDebug()) {
                            logger.debug(Thread.currentThread().getName() + " will process mail " + mail.getName());
                        }
                        attemptDelivery(mail);
                        LifecycleUtil.dispose(mail);
                        mail = null;
                        queueItem.done(true);
                    } catch (Exception e) {
                        // Prevent unexpected exceptions from causing looping by removing message from outgoing.
                        // DO NOT CHANGE THIS to catch Error!
                        // For example, if there were an OutOfMemory condition caused because
                        // something else in the server was abusing memory, we would not want to start purging the retrying spool!
                        logger.error("Exception caught in RemoteDelivery.run()", e);
                        LifecycleUtil.dispose(mail);
                        queueItem.done(false);
                    }

                } catch (Throwable e) {
                    if (!volatileIsDestroyed.isDestroyed()) {
                        logger.error("Exception caught in RemoteDelivery.run()", e);
                    }
                }
            }
        } finally {
            // Restore the thread state to non-interrupted.
            Thread.interrupted();
        }
    }

    private void attemptDelivery(Mail mail) throws MailQueue.MailQueueException {
        ExecutionResult executionResult = deliver(mail);
        switch (executionResult.getExecutionState()) {
            case SUCCESS:
                outgoingMailsMetric.increment();
                break;
            case TEMPORARY_FAILURE:
                handleTemporaryFailure(mail, executionResult);
                break;
            case PERMANENT_FAILURE:
                bouncer.bounce(mail, executionResult.getException().orNull());
                break;
        }
    }

    private void handleTemporaryFailure(Mail mail, ExecutionResult executionResult) throws MailQueue.MailQueueException {
        if (!mail.getState().equals(Mail.ERROR)) {
            mail.setState(Mail.ERROR);
            DeliveryRetriesHelper.initRetries(mail);
            mail.setLastUpdated(new Date());
        }
        int retries = DeliveryRetriesHelper.retrieveRetries(mail);

        if (retries < configuration.getMaxRetries()) {
            reAttemptDelivery(mail, retries);
        } else {
            logger.debug("Bouncing message " + mail.getName() + " after " + retries + " retries");
            bouncer.bounce(mail, new Exception("Too many retries failure. Bouncing after " + retries + " retries.", executionResult.getException().orNull()));
        }
    }

    private void reAttemptDelivery(Mail mail, int retries) throws MailQueue.MailQueueException {
        logger.debug("Storing message " + mail.getName() + " into outgoing after " + retries + " retries");
        DeliveryRetriesHelper.incrementRetries(mail);
        mail.setLastUpdated(new Date());
        // Something happened that will delay delivery. Store it back in the retry repository.
        long delay = getNextDelay(DeliveryRetriesHelper.retrieveRetries(mail));

        if (configuration.isUsePriority()) {
            // Use lowest priority for retries. See JAMES-1311
            mail.setAttribute(MailPrioritySupport.MAIL_PRIORITY, MailPrioritySupport.LOW_PRIORITY);
        }
        queue.enQueue(mail, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * We can assume that the recipients of this message are all going to the
     * same mail server. We will now rely on the DNS server to do DNS MX record
     * lookup and try to deliver to the multiple mail servers. If it fails, it
     * should throw an exception.
     *
     * @param mail    org.apache.james.core.MailImpl
     * @param session javax.mail.Session
     * @return boolean Whether the delivery was successful and the message can
     *         be deleted
     */
    private ExecutionResult deliver(Mail mail) {
        try {
            return tryDeliver(mail);
        } catch (SendFailedException sfe) {
            return handleSenderFailedException(mail, sfe);
        } catch (MessagingException ex) {
            // We should do a better job checking this... if the failure is a general
            // connect exception, this is less descriptive than more specific SMTP command
            // failure... have to lookup and see what are the various Exception possibilities

            // Unable to deliver message after numerous tries... fail accordingly

            // We check whether this is a 5xx error message, which indicates a permanent failure (like account doesn't exist
            // or mailbox is full or domain is setup wrong). We fail permanently if this was a 5xx error

            boolean isPermanent = '5' == ex.getMessage().charAt(0);
            ExecutionResult executionResult = ExecutionResult.onFailure(isPermanent, ex);
            logger.debug(messageComposer.composeFailLogMessage(mail, executionResult));
            return executionResult;
        } catch (Exception ex) {
            logger.error("Generic exception = permanent failure: " + ex.getMessage(), ex);
            // Generic exception = permanent failure
            ExecutionResult executionResult = ExecutionResult.permanentFailure(ex);
            logger.debug(messageComposer.composeFailLogMessage(mail, executionResult));
            return executionResult;
        }
    }

    private ExecutionResult tryDeliver(Mail mail) throws MessagingException {
        if (mail.getRecipients().isEmpty()) {
            logger.info("No recipients specified... not sure how this could have happened.");
            return ExecutionResult.permanentFailure(new Exception("No recipients specified for " + mail.getName() + " sent by " + mail.getSender()));
        }
        if (configuration.isDebug()) {
            logger.debug("Attempting to deliver " + mail.getName());
        }

        // Figure out which servers to try to send to. This collection
        // will hold all the possible target servers
        Iterator<HostAddress> targetServers;
        if (configuration.getGatewayServer().isEmpty()) {
            MailAddress rcpt = mail.getRecipients().iterator().next();
            String host = rcpt.getDomain();

            // Lookup the possible targets
            try {
                targetServers = new MXHostAddressIterator(dnsServer.findMXRecords(host).iterator(), dnsServer, false, logger);
            } catch (TemporaryResolutionException e) {
                return handleTemporaryResolutionException(mail, host);
            }
            if (!targetServers.hasNext()) {
                return handleNoTargetServer(mail, host);
            }
        } else {
            targetServers = getGatewaySMTPHostAddresses(configuration.getGatewayServer());
        }

        return doDeliver(mail, mail.getMessage(), convertToInetAddr(mail.getRecipients()), targetServers);
    }

    private ExecutionResult doDeliver(Mail mail, MimeMessage message, InternetAddress[] addr, Iterator<HostAddress> targetServers) throws MessagingException {
        MessagingException lastError = null;

        while (targetServers.hasNext()) {
            try {
                if (mailDelivrerToHost.tryDeliveryToHost(mail, message, addr, targetServers.next())) {
                    return ExecutionResult.success();
                }
            } catch (SendFailedException sfe) {
                lastError = handleSendFailException(mail, sfe);
            } catch (MessagingException me) {
                lastError = handleMessagingException(mail, me);
                if (configuration.isDebug()) {
                    logger.debug(me.getMessage(), me.getCause());
                } else {
                    logger.info(me.getMessage());
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
        logger.debug("Exception delivering message (" + mail.getName() + ") - " + me.getMessage());
        if ((me.getNextException() != null) && (me.getNextException() instanceof IOException)) {
            // This is more than likely a temporary failure

            // If it's an IO exception with no nested exception, it's probably
            // some socket or weird I/O related problem.
            return me;
        } else {
            // This was not a connection or I/O error particular to one
            // SMTP server of an MX set. Instead, it is almost certainly
            // a protocol level error. In this case we assume that this
            // is an error we'd encounter with any of the SMTP servers
            // associated with this MX record, and we pass the exception
            // to the code in the outer block that determines its
            // severity.
            throw me;
        }
    }

    private ExecutionResult handleSenderFailedException(Mail mail, SendFailedException sfe) {
        logSendFailedException(sfe);

        // Copy the recipients as direct modification may not be possible
        Collection<MailAddress> recipients = new ArrayList<MailAddress>(mail.getRecipients());

        ExecutionResult deleteMessage = ExecutionResult.temporaryFailure();
        EnhancedMessagingException enhancedMessagingException = new EnhancedMessagingException(sfe);

            /*
             * If you send a message that has multiple invalid addresses, you'll
             * get a top-level SendFailedException that that has the valid,
             * valid-unsent, and invalid address lists, with all of the server
             * response messages will be contained within the nested exceptions.
             * [Note: the content of the nested exceptions is implementation
             * dependent.]
             *
             * sfe.getInvalidAddresses() should be considered permanent.
             * sfe.getValidUnsentAddresses() should be considered temporary.
             *
             * JavaMail v1.3 properly populates those collections based upon the
             * 4xx and 5xx response codes to RCPT TO. Some servers, such as
             * Yahoo! don't respond to the RCPT TO, and provide a 5xx reply
             * after DATA. In that case, we will pick up the failure from
             * SMTPSendFailedException.
             */

            /*
             * SMTPSendFailedException introduced in JavaMail 1.3.2, and
             * provides detailed protocol reply code for the operation
             */
        if (enhancedMessagingException.hasReturnCode()) {
            if (enhancedMessagingException.isServerError()) {
                deleteMessage = ExecutionResult.permanentFailure(sfe);
            } else {
                deleteMessage = ExecutionResult.temporaryFailure(sfe);
            }
        }

        // log the original set of intended recipients
        if (configuration.isDebug())
            logger.debug("Recipients: " + recipients);

        if (sfe.getInvalidAddresses() != null) {
            Address[] address = sfe.getInvalidAddresses();
            if (address.length > 0) {
                recipients.clear();
                for (Address addres : address) {
                    try {
                        recipients.add(new MailAddress(addres.toString()));
                    } catch (ParseException pe) {
                        // this should never happen ... we should have
                        // caught malformed addresses long before we
                        // got to this code.
                        logger.debug("Can't parse invalid address: " + pe.getMessage());
                    }
                }
                // Set the recipients for the mail
                mail.setRecipients(recipients);

                if (configuration.isDebug())
                    logger.debug("Invalid recipients: " + recipients);
                deleteMessage = ExecutionResult.permanentFailure(sfe);
                logger.debug(messageComposer.composeFailLogMessage(mail, deleteMessage));
            }
        }

        if (sfe.getValidUnsentAddresses() != null) {
            Address[] address = sfe.getValidUnsentAddresses();
            if (address.length > 0) {
                recipients.clear();
                for (Address addres : address) {
                    try {
                        recipients.add(new MailAddress(addres.toString()));
                    } catch (ParseException pe) {
                        // this should never happen ... we should have
                        // caught malformed addresses long before we
                        // got to this code.
                        logger.debug("Can't parse unsent address: " + pe.getMessage());
                    }
                }
                // Set the recipients for the mail
                mail.setRecipients(recipients);
                if (configuration.isDebug())
                    logger.debug("Unsent recipients: " + recipients);

                if (enhancedMessagingException.hasReturnCode()) {
                    boolean isPermanent = enhancedMessagingException.isServerError();
                    deleteMessage = ExecutionResult.onFailure(isPermanent, sfe);
                    logger.debug(messageComposer.composeFailLogMessage(mail, deleteMessage));
                } else {
                    deleteMessage = ExecutionResult.temporaryFailure(sfe);
                    logger.debug(messageComposer.composeFailLogMessage(mail, deleteMessage));
                }
            }
        }


        return deleteMessage;
    }

    private MessagingException handleSendFailException(Mail mail, SendFailedException sfe) throws SendFailedException {
        logSendFailedException(sfe);

        if (sfe.getValidSentAddresses() != null) {
            Address[] validSent = sfe.getValidSentAddresses();
            if (validSent.length > 0) {
                logger.debug( "Mail (" + mail.getName() + ") sent successfully for " + Arrays.asList(validSent));
            }
        }

        /*
        * SMTPSendFailedException introduced in JavaMail 1.3.2, and
        * provides detailed protocol reply code for the operation
        */
        EnhancedMessagingException enhancedMessagingException = new EnhancedMessagingException(sfe);
        if (enhancedMessagingException.isServerError()) {
            throw sfe;
        }

        if (sfe.getValidUnsentAddresses() != null && sfe.getValidUnsentAddresses().length > 0) {
            if (configuration.isDebug())
                logger.debug("Send failed, " + sfe.getValidUnsentAddresses().length + " valid addresses remain, continuing with any other servers");
            return sfe;
        } else {
            // There are no valid addresses left to send, so rethrow
            throw sfe;
        }
    }

    private InternetAddress[] convertToInetAddr(Collection<MailAddress> recipients) {
        InternetAddress addr[] = new InternetAddress[recipients.size()];
        int j = 0;
        for (Iterator<MailAddress> i = recipients.iterator(); i.hasNext(); j++) {
            MailAddress rcpt = i.next();
            addr[j] = rcpt.toInternetAddress();
        }
        return addr;
    }

    private ExecutionResult handleTemporaryResolutionException(Mail mail, String host) {
        ExecutionResult executionResult = ExecutionResult.temporaryFailure(new MessagingException("Temporary problem looking " +
            "up mail server for host: " + host + ".  I cannot determine where to send this message."));
        logger.debug(messageComposer.composeFailLogMessage(mail, executionResult));
        return executionResult;
    }

    private ExecutionResult handleNoTargetServer(Mail mail, String host) {
        logger.info("No mail server found for: " + host);
        String exceptionBuffer = "There are no DNS entries for the hostname " + host + ".  I cannot determine where to send this message.";

        MessagingException messagingException = new MessagingException(exceptionBuffer);
        int retry = DeliveryRetriesHelper.retrieveRetries(mail);
        if (retry == 0 || retry > configuration.getDnsProblemRetry()) {
            // The domain has no dns entry.. Return a permanent error
            ExecutionResult executionResult = ExecutionResult.permanentFailure(messagingException);
            logger.debug(messageComposer.composeFailLogMessage(mail, executionResult));
            return executionResult;
        } else {
            ExecutionResult executionResult = ExecutionResult.temporaryFailure(messagingException);
            logger.debug(messageComposer.composeFailLogMessage(mail, executionResult));
            return executionResult;
        }
    }

    private long getNextDelay(int retry_count) {
        if (retry_count > configuration.getDelayTimes().size()) {
            return Delay.DEFAULT_DELAY_TIME;
        }
        return configuration.getDelayTimes().get(retry_count - 1);
    }

    private void logSendFailedException(SendFailedException sfe) {
        if (configuration.isDebug()) {
            EnhancedMessagingException enhancedMessagingException = new EnhancedMessagingException(sfe);
            if (enhancedMessagingException.hasReturnCode()) {
                logger.debug("SMTP SEND FAILED:");
                logger.debug(sfe.toString());
                logger.debug("  Command: " + enhancedMessagingException.computeCommand());
                logger.debug("  RetCode: " + enhancedMessagingException.getReturnCode());
                logger.debug("  Response: " + sfe.getMessage());
            } else {
                logger.debug("Send failed: " + sfe.toString());
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
                logger.debug("ADDRESS " + enhancedMessagingException.computeAction() + ":");
                logger.debug(me.toString());
                logger.debug("  Address: " + enhancedMessagingException.computeAddress());
                logger.debug("  Command: " + enhancedMessagingException.computeCommand());
                logger.debug("  RetCode: " + enhancedMessagingException.getReturnCode());
                logger.debug("  Response: " + me.getMessage());
            }
        }
    }

    /**
     * Returns an Iterator over org.apache.mailet.HostAddress, a specialized
     * subclass of javax.mail.URLName, which provides location information for
     * servers that are specified as mail handlers for the given hostname. If no
     * host is found, the Iterator returned will be empty and the first call to
     * hasNext() will return false. The Iterator is a nested iterator: the outer
     * iteration is over each gateway, and the inner iteration is over
     * potentially multiple A records for each gateway.
     *
     * @param gatewayServers - Collection of host[:port] Strings
     * @return an Iterator over HostAddress instances, sorted by priority
     * @since v2.2.0a16-unstable
     */
    private Iterator<HostAddress> getGatewaySMTPHostAddresses(Collection<String> gatewayServers) {
        return new MXHostAddressIterator(gatewayServers.iterator(), dnsServer, false, logger);
    }
}
