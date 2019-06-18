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

package org.apache.james.mailetcontainer.impl;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.dnsservice.library.MXHostAddressIterator;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.server.core.MailImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class JamesMailetContext implements MailetContext, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JamesMailetContext.class);

    /**
     * A hash table of server attributes These are the MailetContext attributes
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    protected final DNSService dns;
    private final UsersRepository localusers;
    private final DomainList domains;
    private final MailQueueFactory<?> mailQueueFactory;
    private MailQueue rootMailQueue;
    private MailAddress postmaster;

    @Inject
    public JamesMailetContext(DNSService dns, UsersRepository localusers, DomainList domains, MailQueueFactory<?> mailQueueFactory) {
        this.dns = dns;
        this.localusers = localusers;
        this.domains = domains;
        this.mailQueueFactory = mailQueueFactory;
    }

    @Override
    public Collection<String> getMailServers(Domain host) {
        try {
            return dns.findMXRecords(host.asString());
        } catch (TemporaryResolutionException e) {
            // TODO: We only do this to not break backward compatiblity. Should
            // fixed later
            return ImmutableSet.of();
        }
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object object) {
        attributes.put(key, object);
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * This generates a response to the Return-Path address, or the address of
     * the message's sender if the Return-Path is not available. Note that this
     * is different than a mail-client's reply, which would use the Reply-To or
     * From header. This will send the bounce with the server's postmaster as
     * the sender.
     */
    @Override
    public void bounce(Mail mail, String message) throws MessagingException {
        bounce(mail, message, getPostmaster());
    }

    /**
     * <p>
     * This generates a response to the Return-Path address, or the address of
     * the message's sender if the Return-Path is not available. Note that this
     * is different than a mail-client's reply, which would use the Reply-To or
     * From header.
     * </p>
     * <p>
     * Bounced messages are attached in their entirety (headers and content) and
     * the resulting MIME part type is "message/rfc822".
     * </p>
     * <p>
     * The attachment to the subject of the original message (or "No Subject" if
     * there is no subject in the original message)
     * </p>
     * <p>
     * There are outstanding issues with this implementation revolving around
     * handling of the return-path header.
     * </p>
     * <p>
     * MIME layout of the bounce message:
     * </p>
     * <p>
     * multipart (mixed)/ contentPartRoot (body) = mpContent (alternative)/ part
     * (body) = message part (body) = original
     * </p>
     */
    @Override
    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        if (!mail.hasSender()) {
            LOGGER.info("Mail to be bounced contains a null (<>) reverse path.  No bounce will be sent.");
            return;
        } else {
            // Bounce message goes to the reverse path, not to the Reply-To
            // address
            LOGGER.info("Processing a bounce request for a message with a reverse path of {}", mail.getMaybeSender());
        }

        MailImpl reply = rawBounce(mail, message);

        // Change the sender...
        if (bouncer != null) {
            reply.getMessage().setFrom(bouncer.toInternetAddress());
        }

        reply.getMessage().saveChanges();
        // Send it off ... with null reverse-path
        reply.setSender(null);
        sendMail(reply);
        LifecycleUtil.dispose(reply);
    }

    @Override
    public List<String> dnsLookup(String s, RecordType recordType) throws LookupException {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    /**
     * Generates a bounce mail that is a bounce of the original message.
     *
     * @param bounceText the text to be prepended to the message to describe the bounce
     *                   condition
     * @return the bounce mail
     * @throws MessagingException if the bounce mail could not be created
     */
    private MailImpl rawBounce(Mail mail, String bounceText) throws MessagingException {
        Preconditions.checkArgument(mail.hasSender(), "Mail should have a sender");
        // This sends a message to the james component that is a bounce of the sent message
        MimeMessage original = mail.getMessage();
        MimeMessage reply = (MimeMessage) original.reply(false);
        reply.setSubject("Re: " + original.getSubject());
        reply.setSentDate(new Date());
        Collection<MailAddress> recipients = mail.getMaybeSender().asList();
        MailAddress sender = mail.getMaybeSender().get();

        reply.setRecipient(Message.RecipientType.TO, new InternetAddress(mail.getMaybeSender().asString()));
        reply.setFrom(new InternetAddress(mail.getRecipients().iterator().next().toString()));
        reply.setText(bounceText);
        reply.setHeader(RFC2822Headers.MESSAGE_ID, "replyTo-" + mail.getName());
        return MailImpl.builder()
            .name("replyTo-" + mail.getName())
            .sender(sender)
            .addRecipients(recipients)
            .mimeMessage(reply)
            .build();
    }

    @Override
    public boolean isLocalUser(String name) {
        if (name == null) {
            return false;
        }
        try {
            if (!name.contains("@")) {
                try {
                    return isLocalEmail(new MailAddress(name.toLowerCase(Locale.US), domains.getDefaultDomain().asString()));
                } catch (DomainListException e) {
                    LOGGER.error("Unable to access DomainList", e);
                    return false;
                }
            } else {
                return isLocalEmail(new MailAddress(name.toLowerCase(Locale.US)));
            }
        } catch (ParseException e) {
            LOGGER.info("Error checking isLocalUser for user {}", name, e);
            return false;
        }
    }

    @Override
    public boolean isLocalEmail(MailAddress mailAddress) {
        if (mailAddress != null) {
            if (!isLocalServer(mailAddress.getDomain())) {
                return false;
            }
            try {
                return localusers.contains(localusers.getUser(mailAddress));
            } catch (UsersRepositoryException e) {
                LOGGER.error("Unable to access UsersRepository", e);
            }
        }
        return false;
    }

    @Override
    public MailAddress getPostmaster() {
        return postmaster;
    }

    @Override
    public int getMajorVersion() {
        return 2;
    }

    @Override
    public int getMinorVersion() {
        return 4;
    }

    /**
     * Performs DNS lookups as needed to find servers which should or might
     * support SMTP. Returns an Iterator over HostAddress, a specialized
     * subclass of javax.mail.URLName, which provides location information for
     * servers that are specified as mail handlers for the given hostname. This
     * is done using MX records, and the HostAddress instances are returned
     * sorted by MX priority. If no host is found for domainName, the Iterator
     * returned will be empty and the first call to hasNext() will return false.
     *
     * @param domainName - the domain for which to find mail servers
     * @return an Iterator over HostAddress instances, sorted by priority
     * @see org.apache.james.dnsservice.api.DNSService#getHostName(java.net.InetAddress)
     * @since Mailet API v2.2.0a16-unstable
     */
    @Override
    @Deprecated
    public Iterator<org.apache.mailet.HostAddress> getSMTPHostAddresses(Domain domainName) {
        try {
            return new MXHostAddressIterator(dns.findMXRecords(domainName.asString()).iterator(), dns, false);
        } catch (TemporaryResolutionException e) {
            // TODO: We only do this to not break backward compatiblity. Should
            // fixed later
            return ImmutableSet.<org.apache.mailet.HostAddress>of().iterator();
        }
    }

    @Override
    public String getServerInfo() {
        return "Apache JAMES";
    }

    @Override
    public boolean isLocalServer(Domain domain) {
        try {
            return domains.containsDomain(domain);
        } catch (DomainListException e) {
            LOGGER.error("Unable to retrieve domains", e);
            return false;
        }
    }

    @Override
    @Deprecated
    public void log(String arg0) {
        LOGGER.info(arg0);
    }

    @Override
    @Deprecated
    public void log(String arg0, Throwable arg1) {
        LOGGER.info(arg0, arg1);
    }

    @Override
    @Deprecated
    public void log(LogLevel logLevel, String s) {
        switch (logLevel) {
            case INFO:
                LOGGER.info(s);
                break;
            case WARN:
                LOGGER.warn(s);
                break;
            case ERROR:
                LOGGER.error(s);
                break;
            default:
                LOGGER.debug(s);
        }
    }

    @Override
    @Deprecated
    public void log(LogLevel logLevel, String s, Throwable throwable) {
        switch (logLevel) {
            case INFO:
                LOGGER.info(s, throwable);
                break;
            case WARN:
                LOGGER.warn(s, throwable);
                break;
            case ERROR:
                LOGGER.error(s, throwable);
                break;
            default:
                LOGGER.debug(s, throwable);
        }
    }

    /**
     * Place a mail on the spool for processing
     *
     * @param message the message to send
     * @throws MessagingException if an exception is caught while placing the mail on the spool
     */
    @Override
    public void sendMail(MimeMessage message) throws MessagingException {
        MailAddress sender = new MailAddress((InternetAddress) message.getFrom()[0]);
        Collection<MailAddress> recipients = new HashSet<>();
        Address[] addresses = message.getAllRecipients();
        if (addresses != null) {
            for (Address address : addresses) {
                // Javamail treats the "newsgroups:" header field as a
                // recipient, so we want to filter those out.
                if (address instanceof InternetAddress) {
                    recipients.add(new MailAddress((InternetAddress) address));
                }
            }
        }
        sendMail(sender, recipients, message);
    }

    @Override
    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage message) throws MessagingException {
        sendMail(sender, recipients, message, Mail.DEFAULT);
    }

    @Override
    public void sendMail(Mail mail) throws MessagingException {
        sendMail(mail, Optional.ofNullable(mail.getState()).orElse(Mail.DEFAULT));
    }

    @Override
    public void sendMail(Mail mail, long delay, TimeUnit unit) throws MessagingException {
        sendMail(mail, Mail.DEFAULT, delay, unit);
    }

    @Override
    public void sendMail(Mail mail, String state) throws MessagingException {
        mail.setAttribute(Mail.SENT_BY_MAILET_ATTRIBUTE);
        mail.setState(state);
        rootMailQueue.enQueue(mail);
    }
    
    @Override
    public void sendMail(Mail mail, String state, long delay, TimeUnit unit) throws MessagingException {
        mail.setAttribute(Mail.SENT_BY_MAILET_ATTRIBUTE);
        mail.setState(state);
        rootMailQueue.enQueue(mail, delay, unit);
    }

    @Override
    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage message, String state) throws MessagingException {
        MailImpl mail = MailImpl.builder()
            .name(MailImpl.getId())
            .sender(sender)
            .addRecipients(recipients)
            .mimeMessage(message)
            .build();
        try {
            sendMail(mail, state);
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    /**
     * <p>
     * This method has been moved to LocalDelivery (the only client of the
     * method). Now we can safely remove it from the Mailet API and from this
     * implementation of MailetContext.
     * </p>
     * <p>
     * The local field localDeliveryMailet will be removed when we remove the
     * storeMail method.
     * </p>
     *
     * @deprecated since 2.2.0 look at the LocalDelivery code to find out how to
     *             do the local delivery.
     */
    public void storeMail(MailAddress sender, MailAddress recipient, MimeMessage msg) {
        throw new UnsupportedOperationException("Was removed");
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        this.rootMailQueue = mailQueueFactory.createQueue(MailQueueFactory.SPOOL);
        try {

            // Get postmaster
            String postMasterAddress = config.getString("postmaster", "postmaster").toLowerCase(Locale.US);
            // if there is no @domain part, then add the first one from the
            // list of supported domains that isn't localhost. If that
            // doesn't work, use the hostname, even if it is localhost.
            if (postMasterAddress.indexOf('@') < 0) {
                Domain domainName = domains.getDomains().stream()
                    .filter(domain -> !Domain.LOCALHOST.equals(domain))
                    .findFirst()
                    .orElse(domains.getDefaultDomain());

                postMasterAddress = postMasterAddress + "@" + domainName.asString();
            }
            try {
                this.postmaster = new MailAddress(postMasterAddress);
                if (!domains.containsDomain(postmaster.getDomain())) {
                    LOGGER.warn("The specified postmaster address ( {} ) is not a local " +
                        "address.  This is not necessarily a problem, but it does mean that emails addressed to " +
                        "the postmaster will be routed to another server.  For some configurations this may " +
                        "cause problems.", postmaster);
                }
            } catch (AddressException e) {
                throw new ConfigurationException("Postmaster address " + postMasterAddress + "is invalid", e);
            }
        } catch (DomainListException e) {
            throw new ConfigurationException("Unable to access DomainList", e);
        }
    }

    @Override
    public Logger getLogger() {
        return LOGGER;
    }
}
