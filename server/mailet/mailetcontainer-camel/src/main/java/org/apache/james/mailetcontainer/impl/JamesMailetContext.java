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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

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
import org.apache.james.core.MailImpl;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.dnsservice.library.MXHostAddressIterator;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.HostAddress;
import org.apache.mailet.LookupException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

@SuppressWarnings("deprecation")
public class JamesMailetContext implements MailetContext, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JamesMailetContext.class);

    /**
     * A hash table of server attributes These are the MailetContext attributes
     */
    private final Hashtable<String, Object> attributes = new Hashtable<>();
    protected DNSService dns;

    private UsersRepository localusers;

    private MailQueue rootMailQueue;

    private DomainList domains;

    private MailAddress postmaster;

    @Inject
    public void retrieveRootMailQueue(MailQueueFactory mailQueueFactory) {
        this.rootMailQueue = mailQueueFactory.getQueue(MailQueueFactory.SPOOL);
    }

    @Inject
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }

    @Inject
    public void setUsersRepository(UsersRepository localusers) {
        this.localusers = localusers;
    }

    @Inject
    public void setDomainList(DomainList domains) {
        this.domains = domains;
    }

    @Override
    public Collection<String> getMailServers(String host) {
        try {
            return dns.findMXRecords(host);
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
        Vector<String> names = new Vector<>();
        for (Enumeration<String> e = attributes.keys(); e.hasMoreElements(); ) {
            names.add(e.nextElement());
        }
        return names.iterator();
    }

    /**
     * This generates a response to the Return-Path address, or the address of
     * the message's sender if the Return-Path is not available. Note that this
     * is different than a mail-client's reply, which would use the Reply-To or
     * From header. This will send the bounce with the server's postmaster as
     * the sender.
     *
     * @see org.apache.mailet.MailetContext#bounce(Mail, String)
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
     *
     * @see org.apache.mailet.MailetContext#bounce(Mail, String, MailAddress)
     */
    @Override
    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        if (mail.getSender() == null) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Mail to be bounced contains a null (<>) reverse path.  No bounce will be sent.");
            return;
        } else {
            // Bounce message goes to the reverse path, not to the Reply-To
            // address
            if (LOGGER.isInfoEnabled())
                LOGGER.info("Processing a bounce request for a message with a reverse path of " + mail.getSender().toString());
        }

        MailImpl reply = rawBounce(mail, message);
        // Change the sender...
        reply.getMessage().setFrom(bouncer.toInternetAddress());
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
        // This sends a message to the james component that is a bounce of the
        // sent message
        MimeMessage original = mail.getMessage();
        MimeMessage reply = (MimeMessage) original.reply(false);
        reply.setSubject("Re: " + original.getSubject());
        reply.setSentDate(new Date());
        Collection<MailAddress> recipients = new HashSet<>();
        recipients.add(mail.getSender());
        InternetAddress addr[] = {new InternetAddress(mail.getSender().toString())};
        reply.setRecipients(Message.RecipientType.TO, addr);
        reply.setFrom(new InternetAddress(mail.getRecipients().iterator().next().toString()));
        reply.setText(bounceText);
        reply.setHeader(RFC2822Headers.MESSAGE_ID, "replyTo-" + mail.getName());
        return new MailImpl("replyTo-" + mail.getName(), new MailAddress(mail.getRecipients().iterator().next().toString()), recipients, reply);
    }

    @Override
    public boolean isLocalUser(String name) {
        if (name == null) {
            return false;
        }
        try {
            if (!name.contains("@")) {
                try {
                    return isLocalEmail(new MailAddress(name.toLowerCase(Locale.US), domains.getDefaultDomain()));
                } catch (DomainListException e) {
                    log("Unable to access DomainList", e);
                    return false;
                }
            } else {
                return isLocalEmail(new MailAddress(name.toLowerCase(Locale.US)));
            }
        } catch (ParseException e) {
            log("Error checking isLocalUser for user " + name);
            return false;
        }
    }

    @Override
    public boolean isLocalEmail(MailAddress mailAddress) {
        if (mailAddress != null) {
            if (!isLocalServer(mailAddress.getDomain().toLowerCase(Locale.US))) {
                return false;
            }
            try {
                return localusers.contains(localusers.getUser(mailAddress));
            } catch (UsersRepositoryException e) {
                log("Unable to access UsersRepository", e);
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
    public Iterator<HostAddress> getSMTPHostAddresses(String domainName) {
        try {
            return new MXHostAddressIterator(dns.findMXRecords(domainName).iterator(), dns, false);
        } catch (TemporaryResolutionException e) {
            // TODO: We only do this to not break backward compatiblity. Should
            // fixed later
            return ImmutableSet.<HostAddress>of().iterator();
        }
    }

    @Override
    public String getServerInfo() {
        return "Apache JAMES";
    }

    @Override
    public boolean isLocalServer(String name) {
        try {
            return domains.containsDomain(name);
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
        Address addresses[] = message.getAllRecipients();
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

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage message) throws MessagingException {
        sendMail(sender, recipients, message, Mail.DEFAULT);
    }

    @Override
    public void sendMail(Mail mail) throws MessagingException {
        mail.setAttribute(Mail.SENT_BY_MAILET, "true");
        rootMailQueue.enQueue(mail);
    }

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage message, String state) throws MessagingException {
        MailImpl mail = new MailImpl(MailImpl.getId(), sender, recipients, message);
        try {
            mail.setState(state);
            sendMail(mail);
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
        try {

            // Get postmaster
            String postMasterAddress = config.getString("postmaster", "postmaster").toLowerCase(Locale.US);
            // if there is no @domain part, then add the first one from the
            // list of supported domains that isn't localhost. If that
            // doesn't work, use the hostname, even if it is localhost.
            if (postMasterAddress.indexOf('@') < 0) {
                String domainName = null; // the domain to use
                // loop through candidate domains until we find one or exhaust
                // the
                // list
                for (String dom : domains.getDomains()) {
                    String serverName = dom.toLowerCase(Locale.US);
                    if (!("localhost".equals(serverName))) {
                        domainName = serverName; // ok, not localhost, so
                        // use it
                    }
                }

                // if we found a suitable domain, use it. Otherwise fallback to
                // the
                // host name.
                postMasterAddress = postMasterAddress + "@" + (domainName != null ? domainName : domains.getDefaultDomain());
            }
            try {
                this.postmaster = new MailAddress(postMasterAddress);
                if (!domains.containsDomain(postmaster.getDomain())) {
                    String warnBuffer = "The specified postmaster address ( " + postmaster + " ) is not a local " +
                            "address.  This is not necessarily a problem, but it does mean that emails addressed to " +
                            "the postmaster will be routed to another server.  For some configurations this may " +
                            "cause problems.";
                    LOGGER.warn(warnBuffer);
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
