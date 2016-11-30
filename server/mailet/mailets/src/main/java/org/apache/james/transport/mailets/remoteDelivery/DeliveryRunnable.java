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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
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

import com.sun.mail.smtp.SMTPTransport;

@SuppressWarnings("deprecation")
public class DeliveryRunnable implements Runnable {

    private final MailQueue queue;
    private final RemoteDeliveryConfiguration configuration;
    private final DNSService dnsServer;
    private final Metric outgoingMailsMetric;
    private final Logger logger;
    private final MailetContext mailetContext;
    private final VolatileIsDestroyed volatileIsDestroyed;

    public DeliveryRunnable(MailQueue queue, RemoteDeliveryConfiguration configuration, DNSService dnsServer, Metric outgoingMailsMetric, Logger logger, MailetContext mailetContext, VolatileIsDestroyed volatileIsDestroyed) {
        this.queue = queue;
        this.configuration = configuration;
        this.dnsServer = dnsServer;
        this.outgoingMailsMetric = outgoingMailsMetric;
        this.logger = logger;
        this.mailetContext = mailetContext;
        this.volatileIsDestroyed = volatileIsDestroyed;
    }

    /**
     * Handles checking the outgoing spool for new mail and delivering them if
     * there are any
     */
    @Override
    public void run() {
        final Session session = obtainSession(configuration.createFinalJavaxProperties());
        try {
            while (!Thread.interrupted() && !volatileIsDestroyed.isDestroyed()) {
                try {
                    // Get the 'mail' object that is ready for deliverying. If
                    // no
                    // message is
                    // ready, the 'accept' will block until message is ready.
                    // The amount
                    // of time to block is determined by the 'getWaitTime'
                    // method of the
                    // MultipleDelayFilter.
                    MailQueue.MailQueueItem queueItem = queue.deQueue();
                    Mail mail = queueItem.getMail();

                    String key = mail.getName();

                    try {
                        if (configuration.isDebug()) {
                            String message = Thread.currentThread().getName() + " will process mail " + key;
                            logger.debug(message);
                        }

                        // Deliver message
                        if (deliver(mail, session)) {
                            // Message was successfully delivered/fully
                            // failed...
                            // delete it
                            LifecycleUtil.dispose(mail);
                            // workRepository.remove(key);
                        } else {
                            // Something happened that will delay delivery.
                            // Store it back in the retry repository.
                            // workRepository.store(mail);
                            int retries = 0;
                            try {
                                retries = Integer.parseInt(mail.getErrorMessage());
                            } catch (NumberFormatException e) {
                                // Something strange was happen with the
                                // errorMessage..
                            }

                            long delay = getNextDelay(retries);

                            if (configuration.isUsePriority()) {
                                // Use lowest priority for retries. See JAMES-1311
                                mail.setAttribute(MailPrioritySupport.MAIL_PRIORITY, MailPrioritySupport.LOW_PRIORITY);
                            }
                            queue.enQueue(mail, delay, TimeUnit.MILLISECONDS);
                            LifecycleUtil.dispose(mail);

                            // This is an update, so we have to unlock and
                            // notify or this mail is kept locked by this
                            // thread.
                            // workRepository.unlock(key);

                            // Note: We do not notify because we updated an
                            // already existing mail and we are now free to
                            // handle
                            // more mails.
                            // Furthermore this mail should not be processed now
                            // because we have a retry time scheduling.
                        }

                        // Clear the object handle to make sure it recycles
                        // this object.
                        mail = null;
                        queueItem.done(true);
                    } catch (Exception e) {
                        // Prevent unexpected exceptions from causing looping by
                        // removing message from outgoing.
                        // DO NOT CHANGE THIS to catch Error! For example, if
                        // there were an OutOfMemory condition caused because
                        // something else in the server was abusing memory, we
                        // would
                        // not want to start purging the retrying spool!
                        logger.error("Exception caught in RemoteDelivery.run()", e);
                        LifecycleUtil.dispose(mail);
                        // workRepository.remove(key);
                        queueItem.done(false);
                        throw new MailQueue.MailQueueException("Unable to perform dequeue", e);
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
    private boolean deliver(Mail mail, Session session) {
        try {
            if (configuration.isDebug()) {
                logger.debug("Attempting to deliver " + mail.getName());
            }
            MimeMessage message = mail.getMessage();

            // Create an array of the recipients as InternetAddress objects
            Collection<MailAddress> recipients = mail.getRecipients();
            InternetAddress addr[] = new InternetAddress[recipients.size()];
            int j = 0;
            for (Iterator<MailAddress> i = recipients.iterator(); i.hasNext(); j++) {
                MailAddress rcpt = i.next();
                addr[j] = rcpt.toInternetAddress();
            }

            if (addr.length <= 0) {
                logger.info("No recipients specified... not sure how this could have happened.");
                return true;
            }

            // Figure out which servers to try to send to. This collection
            // will hold all the possible target servers
            Iterator<HostAddress> targetServers;
            if (configuration.getGatewayServer().isEmpty()) {
                MailAddress rcpt = recipients.iterator().next();
                String host = rcpt.getDomain();

                // Lookup the possible targets
                try {
                    targetServers = new MXHostAddressIterator(dnsServer.findMXRecords(host).iterator(), dnsServer, false, logger);
                } catch (TemporaryResolutionException e) {
                    logger.info("Temporary problem looking up mail server for host: " + host);
                    String exceptionBuffer = "Temporary problem looking up mail server for host: " + host + ".  I cannot determine where to send this message.";

                    // temporary problems
                    return failMessage(mail, new MessagingException(exceptionBuffer), false);
                }
                if (!targetServers.hasNext()) {
                    logger.info("No mail server found for: " + host);
                    String exceptionBuffer = "There are no DNS entries for the hostname " + host + ".  I cannot determine where to send this message.";

                    int retry = 0;
                    try {
                        retry = Integer.parseInt(mail.getErrorMessage());
                    } catch (NumberFormatException e) {
                        // Unable to parse retryCount
                    }
                    if (retry == 0 || retry > configuration.getDnsProblemRetry()) {
                        // The domain has no dns entry.. Return a permanent
                        // error
                        return failMessage(mail, new MessagingException(exceptionBuffer), true);
                    } else {
                        return failMessage(mail, new MessagingException(exceptionBuffer), false);
                    }
                }
            } else {
                targetServers = getGatewaySMTPHostAddresses(configuration.getGatewayServer());
            }

            MessagingException lastError = null;

            while (targetServers.hasNext()) {
                try {

                    Properties props = session.getProperties();
                    if (mail.getSender() == null) {
                        props.put("mail.smtp.from", "<>");
                    } else {
                        String sender = mail.getSender().toString();
                        props.put("mail.smtp.from", sender);
                    }

                    HostAddress outgoingMailServer = targetServers.next();
                    StringBuilder logMessageBuffer = new StringBuilder(256).append("Attempting delivery of ").append(mail.getName()).append(" to host ").append(outgoingMailServer.getHostName()).append(" at ").append(outgoingMailServer.getHost()).append(" from ").append(props.get("mail.smtp.from"))
                        .append(" for addresses ").append(Arrays.asList(addr));
                    logger.debug(logMessageBuffer.toString());

                    // Many of these properties are only in later JavaMail
                    // versions
                    // "mail.smtp.ehlo" //default true
                    // "mail.smtp.auth" //default false
                    // "mail.smtp.dsn.ret" //default to nothing... appended as
                    // RET= after MAIL FROM line.
                    // "mail.smtp.dsn.notify" //default to nothing...appended as
                    // NOTIFY= after RCPT TO line.

                    SMTPTransport transport = null;
                    try {
                        transport =  (SMTPTransport) session.getTransport(outgoingMailServer);
                        transport.setLocalHost( props.getProperty("mail.smtp.localhost", configuration.getHeloNameProvider().getHeloName()) );
                        try {
                            if (configuration.getAuthUser() != null) {
                                transport.connect(outgoingMailServer.getHostName(), configuration.getAuthUser(), configuration.getAuthPass());
                            } else {
                                transport.connect();
                            }
                        } catch (MessagingException me) {
                            // Any error on connect should cause the mailet to
                            // attempt
                            // to connect to the next SMTP server associated
                            // with this
                            // MX record. Just log the exception. We'll worry
                            // about
                            // failing the message at the end of the loop.

                            // Also include the stacktrace if debug is enabled. See JAMES-1257
                            if (configuration.isDebug()) {
                                logger.debug(me.getMessage(), me.getCause());
                            } else {
                                logger.info(me.getMessage());
                            }
                            continue;
                        }
                        // if the transport is a SMTPTransport (from sun) some
                        // performance enhancement can be done.
                        if (transport.getClass().getName().endsWith(".SMTPTransport")) {
                            boolean supports8bitmime = false;
                            try {
                                Method supportsExtension = transport.getClass().getMethod("supportsExtension", new Class[]{String.class});
                                supports8bitmime = (Boolean) supportsExtension.invoke(transport, "8BITMIME");
                            } catch (NoSuchMethodException nsme) {
                                // An SMTPAddressFailedException with no
                                // getAddress method.
                            } catch (IllegalAccessException iae) {
                            } catch (IllegalArgumentException iae) {
                            } catch (InvocationTargetException ite) {
                                // Other issues with getAddress invokation.
                            }

                            // if the message is alredy 8bit or binary and the
                            // server doesn't support the 8bit extension it has
                            // to be converted to 7bit. Javamail api doesn't
                            // perform
                            // that conversion, but it is required to be a
                            // rfc-compliant smtp server.

                            // Temporarily disabled. See JAMES-638
                            if (!supports8bitmime) {
                                try {
                                    convertTo7Bit(message);
                                } catch (IOException e) {
                                    // An error has occured during the 7bit
                                    // conversion.
                                    // The error is logged and the message is
                                    // sent anyway.

                                    logger.error("Error during the conversion to 7 bit.", e);
                                }
                            }
                        } else {
                            // If the transport is not the one
                            // developed by Sun we are not sure of how it
                            // handles the 8 bit mime stuff,
                            // so I convert the message to 7bit.
                            try {
                                convertTo7Bit(message);
                            } catch (IOException e) {
                                logger.error("Error during the conversion to 7 bit.", e);
                            }
                        }
                        transport.sendMessage(message, addr);
                    } finally {
                        if (transport != null) {
                            try {
                                // James-899: transport.close() sends QUIT to
                                // the server; if that fails
                                // (e.g. because the server has already closed
                                // the connection) the message
                                // should be considered to be delivered because
                                // the error happened outside
                                // of the mail transaction (MAIL, RCPT, DATA).
                                transport.close();
                            } catch (MessagingException e) {
                                logger.error("Warning: could not close the SMTP transport after sending mail (" + mail.getName() + ") to " + outgoingMailServer.getHostName() + " at " + outgoingMailServer.getHost() + " for " + mail.getRecipients() + "; probably the server has already closed the "
                                    + "connection. Message is considered to be delivered. Exception: " + e.getMessage());
                            }
                            transport = null;
                        }
                    }
                    logMessageBuffer = new StringBuilder(256).append("Mail (").append(mail.getName()).append(") sent successfully to ").append(outgoingMailServer.getHostName()).append(" at ").append(outgoingMailServer.getHost()).append(" from ").append(props.get("mail.smtp.from")).append(" for ")
                        .append(mail.getRecipients());
                    logger.debug(logMessageBuffer.toString());
                    outgoingMailsMetric.increment();
                    return true;
                } catch (SendFailedException sfe) {
                    logSendFailedException(sfe);

                    if (sfe.getValidSentAddresses() != null) {
                        Address[] validSent = sfe.getValidSentAddresses();
                        if (validSent.length > 0) {
                            String logMessageBuffer = "Mail (" + mail.getName() + ") sent successfully for " + Arrays.asList(validSent);
                            logger.debug(logMessageBuffer);
                        }
                    }

                    /*
                     * SMTPSendFailedException introduced in JavaMail 1.3.2, and
                     * provides detailed protocol reply code for the operation
                     */
                    if (sfe.getClass().getName().endsWith(".SMTPSendFailedException")) {
                        try {
                            int returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                            // if 5xx, terminate this delivery attempt by
                            // re-throwing the exception.
                            if (returnCode >= 500 && returnCode <= 599)
                                throw sfe;
                        } catch (ClassCastException cce) {
                        } catch (IllegalArgumentException iae) {
                        }
                    }

                    if (sfe.getValidUnsentAddresses() != null && sfe.getValidUnsentAddresses().length > 0) {
                        if (configuration.isDebug())
                            logger.debug("Send failed, " + sfe.getValidUnsentAddresses().length + " valid addresses remain, continuing with any other servers");
                        lastError = sfe;
                    } else {
                        // There are no valid addresses left to send, so rethrow
                        throw sfe;
                    }
                } catch (MessagingException me) {
                    // MessagingException are horribly difficult to figure out
                    // what actually happened.
                    String exceptionBuffer = "Exception delivering message (" + mail.getName() + ") - " + me.getMessage();
                    logger.debug(exceptionBuffer);
                    if ((me.getNextException() != null) && (me.getNextException() instanceof java.io.IOException)) {
                        // This is more than likely a temporary failure

                        // If it's an IO exception with no nested exception,
                        // it's probably
                        // some socket or weird I/O related problem.
                        lastError = me;
                        continue;
                    }
                    // This was not a connection or I/O error particular to one
                    // SMTP server of an MX set. Instead, it is almost certainly
                    // a protocol level error. In this case we assume that this
                    // is an error we'd encounter with any of the SMTP servers
                    // associated with this MX record, and we pass the exception
                    // to the code in the outer block that determines its
                    // severity.
                    throw me;
                }
            } // end while
            // If we encountered an exception while looping through,
            // throw the last MessagingException we caught. We only
            // do this if we were unable to send the message to any
            // server. If sending eventually succeeded, we exit
            // deliver() though the return at the end of the try
            // block.
            if (lastError != null) {
                throw lastError;
            }
        } catch (SendFailedException sfe) {
            logSendFailedException(sfe);

            // Copy the recipients as direct modification may not be possible
            Collection<MailAddress> recipients = new ArrayList<MailAddress>(mail.getRecipients());

            boolean deleteMessage = false;

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
            try {
                if (sfe.getClass().getName().endsWith(".SMTPSendFailedException")) {
                    int returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                    // If we got an SMTPSendFailedException, use its RetCode to
                    // determine default permanent/temporary failure
                    deleteMessage = (returnCode >= 500 && returnCode <= 599);
                } else {
                    // Sometimes we'll get a normal SendFailedException with
                    // nested SMTPAddressFailedException, so use the latter
                    // RetCode
                    MessagingException me = sfe;
                    Exception ne;
                    while ((ne = me.getNextException()) != null && ne instanceof MessagingException) {
                        me = (MessagingException) ne;
                        if (me.getClass().getName().endsWith(".SMTPAddressFailedException")) {
                            int returnCode = (Integer) invokeGetter(me, "getReturnCode");
                            deleteMessage = (returnCode >= 500 && returnCode <= 599);
                        }
                    }
                }
            } catch (IllegalStateException ise) {
                // unexpected exception (not a compatible javamail
                // implementation)
            } catch (ClassCastException cce) {
                // unexpected exception (not a compatible javamail
                // implementation)
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
                    deleteMessage = failMessage(mail, sfe, true);
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
                    if (sfe.getClass().getName().endsWith(".SMTPSendFailedException")) {
                        int returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                        deleteMessage = failMessage(mail, sfe, returnCode >= 500 && returnCode <= 599);
                    } else {
                        deleteMessage = failMessage(mail, sfe, false);
                    }
                }
            }


            return deleteMessage;
        } catch (MessagingException ex) {
            // We should do a better job checking this... if the failure is a
            // general
            // connect exception, this is less descriptive than more specific
            // SMTP command
            // failure... have to lookup and see what are the various Exception
            // possibilities

            // Unable to deliver message after numerous tries... fail
            // accordingly

            // We check whether this is a 5xx error message, which
            // indicates a permanent failure (like account doesn't exist
            // or mailbox is full or domain is setup wrong).
            // We fail permanently if this was a 5xx error
            return failMessage(mail, ex, ('5' == ex.getMessage().charAt(0)));
        } catch (Exception ex) {
            logger.error("Generic exception = permanent failure: "+ex.getMessage(), ex);
            // Generic exception = permanent failure
            return failMessage(mail, ex, true);
        }

        /*
         * If we get here, we've exhausted the loop of servers without sending
         * the message or throwing an exception. One case where this might
         * happen is if we get a MessagingException on each transport.connect(),
         * e.g., if there is only one server and we get a connect exception.
         */
        return failMessage(mail, new MessagingException("No mail server(s) available at this time."), false);
    }

    /**
     * Returns the javamail Session object.
     *
     * @param props
     * @return the java mail session
     */
    protected Session obtainSession(Properties props) {
        return Session.getInstance(props);
    }


    /**
     * This method returns, given a retry-count, the next delay time to use.
     *
     * @param retry_count the current retry_count.
     * @return the next delay time to use, given the retry count
     */
    private long getNextDelay(int retry_count) {
        if (retry_count > configuration.getDelayTimes().size()) {
            return Delay.DEFAULT_DELAY_TIME;
        }
        return configuration.getDelayTimes().get(retry_count - 1);
    }


    /**
     * Utility method used to invoke getters for javamail implementation
     * specific classes.
     *
     * @param target the object whom method will be invoked
     * @param getter the no argument method name
     * @return the result object
     * @throws IllegalStateException on invocation error
     */
    private Object invokeGetter(Object target, String getter) {
        try {
            Method getAddress = target.getClass().getMethod(getter);
            return getAddress.invoke(target);
        } catch (NoSuchMethodException nsme) {
            // An SMTPAddressFailedException with no getAddress method.
        } catch (IllegalAccessException iae) {
        } catch (IllegalArgumentException iae) {
        } catch (InvocationTargetException ite) {
            // Other issues with getAddress invokation.
        }
        return new IllegalStateException("Exception invoking " + getter + " on a " + target.getClass() + " object");
    }

    /*
     * private method to log the extended SendFailedException introduced in
     * JavaMail 1.3.2.
     */
    private void logSendFailedException(SendFailedException sfe) {
        if (configuration.isDebug()) {
            MessagingException me = sfe;
            if (me.getClass().getName().endsWith(".SMTPSendFailedException")) {
                try {
                    String command = (String) invokeGetter(sfe, "getCommand");
                    Integer returnCode = (Integer) invokeGetter(sfe, "getReturnCode");
                    logger.debug("SMTP SEND FAILED:");
                    logger.debug(sfe.toString());
                    logger.debug("  Command: " + command);
                    logger.debug("  RetCode: " + returnCode);
                    logger.debug("  Response: " + sfe.getMessage());
                } catch (IllegalStateException ise) {
                    // Error invoking the getAddress method
                    logger.debug("Send failed: " + me.toString());
                } catch (ClassCastException cce) {
                    // The getAddress method returned something different than
                    // InternetAddress
                    logger.debug("Send failed: " + me.toString());
                }
            } else {
                logger.debug("Send failed: " + me.toString());
            }
            Exception ne;
            while ((ne = me.getNextException()) != null && ne instanceof MessagingException) {
                me = (MessagingException) ne;
                if (me.getClass().getName().endsWith(".SMTPAddressFailedException") || me.getClass().getName().endsWith(".SMTPAddressSucceededException")) {
                    try {
                        String action = me.getClass().getName().endsWith(".SMTPAddressFailedException") ? "FAILED" : "SUCCEEDED";
                        InternetAddress address = (InternetAddress) invokeGetter(me, "getAddress");
                        String command = (String) invokeGetter(me, "getCommand");
                        Integer returnCode = (Integer) invokeGetter(me, "getReturnCode");
                        logger.debug("ADDRESS " + action + ":");
                        logger.debug(me.toString());
                        logger.debug("  Address: " + address);
                        logger.debug("  Command: " + command);
                        logger.debug("  RetCode: " + returnCode);
                        logger.debug("  Response: " + me.getMessage());
                    } catch (IllegalStateException ise) {
                        // Error invoking the getAddress method
                    } catch (ClassCastException cce) {
                        // A method returned something different than expected
                    }
                }
            }
        }
    }

    /**
     * Converts a message to 7 bit.
     *
     * @param part
     */
    private void convertTo7Bit(MimePart part) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            MimeMultipart parts = (MimeMultipart) part.getContent();
            int count = parts.getCount();
            for (int i = 0; i < count; i++) {
                convertTo7Bit((MimePart) parts.getBodyPart(i));
            }
        } else if ("8bit".equals(part.getEncoding())) {
            // The content may already be in encoded the form (likely with mail
            // created from a
            // stream). In that case, just changing the encoding to
            // quoted-printable will mangle
            // the result when this is transmitted. We must first convert the
            // content into its
            // native format, set it back, and only THEN set the transfer
            // encoding to force the
            // content to be encoded appropriately.

            // if the part doesn't contain text it will be base64 encoded.
            String contentTransferEncoding = part.isMimeType("text/*") ? "quoted-printable" : "base64";
            part.setContent(part.getContent(), part.getContentType());
            part.setHeader("Content-Transfer-Encoding", contentTransferEncoding);
            part.addHeader("X-MIME-Autoconverted", "from 8bit to " + contentTransferEncoding + " by " + mailetContext.getServerInfo());
        }
    }

    /**
     * Insert the method's description here.
     *
     * @param mail      org.apache.james.core.MailImpl
     * @param ex        javax.mail.MessagingException
     * @param permanent
     * @return boolean Whether the message failed fully and can be deleted
     */
    private boolean failMessage(Mail mail, Exception ex, boolean permanent) {
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        if (permanent) {
            out.print("Permanent");
        } else {
            out.print("Temporary");
        }

        String exceptionLog = exceptionToLogString(ex);

        StringBuilder logBuffer = new StringBuilder(64).append(" exception delivering mail (").append(mail.getName());

        if (exceptionLog != null) {
            logBuffer.append(". ");
            logBuffer.append(exceptionLog);
        }

        logBuffer.append(": ");
        out.print(logBuffer.toString());
        if (configuration.isDebug())
            ex.printStackTrace(out);
        logger.debug(sout.toString());
        if (!permanent) {
            if (!mail.getState().equals(Mail.ERROR)) {
                mail.setState(Mail.ERROR);
                mail.setErrorMessage("0");
                mail.setLastUpdated(new Date());
            }

            int retries = 0;
            try {
                retries = Integer.parseInt(mail.getErrorMessage());
            } catch (NumberFormatException e) {
                // Something strange was happen with the errorMessage..
            }

            if (retries < configuration.getMaxRetries()) {
                logBuffer = new StringBuilder(128).append("Storing message ").append(mail.getName()).append(" into outgoing after ").append(retries).append(" retries");
                logger.debug(logBuffer.toString());
                ++retries;
                mail.setErrorMessage(retries + "");
                mail.setLastUpdated(new Date());
                return false;
            } else {
                logBuffer = new StringBuilder(128).append("Bouncing message ").append(mail.getName()).append(" after ").append(retries).append(" retries");
                logger.debug(logBuffer.toString());
            }
        }

        if (mail.getSender() == null) {
            logger.debug("Null Sender: no bounce will be generated for " + mail.getName());
            return true;
        }

        if (configuration.getBounceProcessor() != null) {
            // do the new DSN bounce
            // setting attributes for DSN mailet
            String cause;
            if (ex instanceof MessagingException) {
                cause = getErrorMsg((MessagingException) ex);
            } else {
                cause = ex.getMessage();
            }
            mail.setAttribute("delivery-error", cause);
            mail.setState(configuration.getBounceProcessor());
            // re-insert the mail into the spool for getting it passed to the
            // dsn-processor
            MailetContext mc = mailetContext;
            try {
                mc.sendMail(mail);
            } catch (MessagingException e) {
                // we shouldn't get an exception, because the mail was already
                // processed
                logger.debug("Exception re-inserting failed mail: ", e);
            }
        } else {
            // do an old style bounce
            bounce(mail, ex);
        }
        return true;
    }


    /**
     * Try to return a usefull logString created of the Exception which was
     * given. Return null if nothing usefull could be done
     *
     * @param e The MessagingException to use
     * @return logString
     */
    private String exceptionToLogString(Exception e) {
        if (e.getClass().getName().endsWith(".SMTPSendFailedException")) {
            return "RemoteHost said: " + e.getMessage();
        } else if (e instanceof SendFailedException) {
            SendFailedException exception = (SendFailedException) e;

            // No error
            if (exception.getInvalidAddresses().length == 0 && exception.getValidUnsentAddresses().length == 0)
                return null;

            Exception ex;
            StringBuilder sb = new StringBuilder();
            boolean smtpExFound = false;
            sb.append("RemoteHost said:");

            if (e instanceof MessagingException)
                while ((ex = ((MessagingException) e).getNextException()) != null && ex instanceof MessagingException) {
                    e = ex;
                    if (ex.getClass().getName().endsWith(".SMTPAddressFailedException")) {
                        try {
                            InternetAddress ia = (InternetAddress) invokeGetter(ex, "getAddress");
                            sb.append(" ( ").append(ia).append(" - [").append(ex.getMessage().replaceAll("\\n", "")).append("] )");
                            smtpExFound = true;
                        } catch (IllegalStateException ise) {
                            // Error invoking the getAddress method
                        } catch (ClassCastException cce) {
                            // The getAddress method returned something
                            // different than InternetAddress
                        }
                    }
                }
            if (!smtpExFound) {
                boolean invalidAddr = false;
                sb.append(" ( ");

                if (exception.getInvalidAddresses().length > 0) {
                    sb.append(Arrays.toString(exception.getInvalidAddresses()));
                    invalidAddr = true;
                }
                if (exception.getValidUnsentAddresses().length > 0) {
                    if (invalidAddr)
                        sb.append(" ");
                    sb.append(Arrays.toString(exception.getValidUnsentAddresses()));
                }
                sb.append(" - [");
                sb.append(exception.getMessage().replaceAll("\\n", ""));
                sb.append("] )");
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * Utility method for getting the error message from the (nested) exception.
     *
     * @param me MessagingException
     * @return error message
     */
    protected String getErrorMsg(MessagingException me) {
        if (me.getNextException() == null) {
            return me.getMessage().trim();
        } else {
            Exception ex1 = me.getNextException();
            return ex1.getMessage().trim();
        }
    }

    private void bounce(Mail mail, Exception ex) {
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        String machine;
        try {
            machine = configuration.getHeloNameProvider().getHeloName();

        } catch (Exception e) {
            machine = "[address unknown]";
        }
        String bounceBuffer = "Hi. This is the James mail server at " + machine + ".";
        out.println(bounceBuffer);
        out.println("I'm afraid I wasn't able to deliver your message to the following addresses.");
        out.println("This is a permanent error; I've given up. Sorry it didn't work out.  Below");
        out.println("I include the list of recipients and the reason why I was unable to deliver");
        out.println("your message.");
        out.println();
        for (MailAddress mailAddress : mail.getRecipients()) {
            out.println(mailAddress);
        }
        if (ex instanceof MessagingException) {
            if (((MessagingException) ex).getNextException() == null) {
                out.println(ex.getMessage().trim());
            } else {
                Exception ex1 = ((MessagingException) ex).getNextException();
                if (ex1 instanceof SendFailedException) {
                    out.println("Remote mail server told me: " + ex1.getMessage().trim());
                } else if (ex1 instanceof UnknownHostException) {
                    out.println("Unknown host: " + ex1.getMessage().trim());
                    out.println("This could be a DNS server error, a typo, or a problem with the recipient's mail server.");
                } else if (ex1 instanceof ConnectException) {
                    // Already formatted as "Connection timed out: connect"
                    out.println(ex1.getMessage().trim());
                } else if (ex1 instanceof SocketException) {
                    out.println("Socket exception: " + ex1.getMessage().trim());
                } else {
                    out.println(ex1.getMessage().trim());
                }
            }
        }
        out.println();

        logger.debug("Sending failure message " + mail.getName());
        try {
            mailetContext.bounce(mail, sout.toString());
        } catch (MessagingException me) {
            logger.debug("Encountered unexpected messaging exception while bouncing message: " + me.getMessage());
        } catch (Exception e) {
            logger.debug("Encountered unexpected exception while bouncing message: " + e.getMessage());
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
        Iterator<String> gateways = gatewayServers.iterator();

        return new MXHostAddressIterator(gateways, dnsServer, false, logger);
    }
}
