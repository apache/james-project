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


package org.apache.mailet;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;

/**
 * Defines a set of methods that can be used to interact with the mailet
 * container. For example, it can be used to send a new message, to deliver
 * a message locally, or to write to a log file.
 * <p/>
 * Mailets and Matchers can retrieve a MailetContext through their
 * respective MailetConfig and MatcherConfig objects, which are provided
 * to them by the mailet container when they are initialized.
 * <p/>
 * <b>Mailet Context Attributes</b>
 * <p/>
 * The Mailet Context can provide additional configuration or other
 * information not defined in this interface to Mailets and Matchers
 * by using attributes. See your server documentation for information
 * on the attributes it provides.
 * <p/>
 * Every attribute consists of a name and a value.
 * Attribute names should follow the same convention as package names.
 * The Mailet API specification reserves names matching
 * <i>org.apache.james.*</i> and <i>org.apache.mailet.*</i>.
 * Attribute values can be arbitrary objects.
 * <p/>
 * The list of attributes which are currently associated with a mailet
 * context can be retrieved using the {@link #getAttributeNames}
 * method, and given its name, the value of an attribute can be
 * retrieved using the {@link #getAttribute} method.
 */
public interface MailetContext {

    /**
     * Loglevel for logging operations
     *
     * @since Mailet API v2.5
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    /**
     * DNS Record Types for lookup operations
     *
     * @since Mailet API v2.5
     */
    enum RecordType {
        A, AAAA, PTR, MX, TXT, SPF
    }

    /**
     * Returns the major version number of the Mailet API that this mailet
     * container supports. For example, if the mailet container supports
     * version 1.2 of the Mailet API, this method returns 1.
     *
     * @return the major version number of the supported Mailet API
     */
    int getMajorVersion();

    /**
     * Returns the minor version number of the Mailet API that this mailet
     * container supports. For example, if the mailet container supports
     * version 1.2 of the Mailet API, this method returns 2.
     *
     * @return the minor version number of the supported Mailet API
     */
    int getMinorVersion();

    /**
     * Returns the name and version of the mailet container on which
     * the mailet is running.
     * <p>
     * The returned string is of the form {@code <servername>/<versionnumber>},
     * optionally followed by additional information in parentheses. For example,
     * the JAMES mailet container may return the string {@code "JAMES/1.2"}
     * or {@code "JAMES/1.2 (JDK 1.3.0; Windows NT 4.0 x86)"}.
     *
     * @return the server information string
     */
    String getServerInfo();

    /**
     * Returns an Iterator over the names of all attributes which are set
     * in this mailet context.
     * <p>
     * The {@link #getAttribute} method can be called to
     * retrieve an attribute's value given its name.
     *
     * @return an Iterator (of Strings) over all attribute names
     */
    @Deprecated
    Iterator<String> getAttributeNames();

    /**
     * Returns the value of the named mailet context attribute,
     * or null if the attribute does not exist.
     *
     * @param name the attribute name
     * @return the attribute value, or null if the attribute does not exist
     */
    @Deprecated
    Object getAttribute(String name);

    /**
     * Associates an attribute with the given name and value with this mailet context.
     * <p>
     * If an attribute with the given name already exists, it is replaced, and the
     * previous value is returned.
     * <p>
     * Attribute names should follow the same convention as package names.
     * The Mailet API specification reserves names matching
     * <i>org.apache.james.*</i> and <i>org.apache.mailet.*</i>.
     *
     * @param name the attribute name
     * @param value the attribute value
     */
    @Deprecated
    void setAttribute(String name, Object value);

    /**
     * Removes the attribute with the given name from this Mail instance.
     *
     * @param name the name of the attribute to be removed
     * @since Mailet API v2.1
     */
    @Deprecated
    void removeAttribute(String name);

    /**
     * Writes the specified message to a mailet log. The name and type of
     * the mailet log is specific to the mailet container.
     *
     * @param message the message to be written to the log
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    void log(String message);

    /**
     * Writes the specified message to a mailet log, along with the stack
     * trace of the given Throwable. The name and type of the mailet log
     * is specific to the mailet container.
     *
     * @param message the message to be written to the log
     * @param t the Throwable whose stack trace is to be written to the log
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    void log(String message, Throwable t);


    /**
     * Writes the specified message to a mailet log. The name and type of
     * the mailet log is specific to the mailet container.
     *
     * @param level the {@link LogLevel} to use
     * @param message the message to be written to the log
     * @since 2.5
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    void log(LogLevel level, String message);

    /**
     * Writes the specified message to a mailet log, along with the stack
     * trace of the given Throwable. The name and type of the mailet log
     * is specific to the mailet container.
     *
     * @param message the message to be written to the log
     * @param t the Throwable whose stack trace is to be written to the log
     * @param level {@link LogLevel} to use
     * @since 2.5
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    void log(LogLevel level, String message, Throwable t);

    /**
     * Returns the Postmaster address for this mailet context.
     *
     * @return the Postmaster address
     */
    MailAddress getPostmaster();

    /**
     * Checks if a host name is local, i.e. this server is the
     * final delivery destination for messages sent to this domain.
     *
     * @param domain the domain name to check
     * @return true if server is local, false otherwise
     */
    boolean isLocalServer(Domain domain);

    /**
     * Checks if a user account is local, i.e. the account exists locally
     * and this server is the final delivery destination for messages
     * sent to this address.
     * <p>
     * This given user account string should contain the full
     * user address, i.e. user@domain. If the domain part is
     * missing, "localhost" will be used as the domain name.
     *
     * @param userAccount the full address of the account to be checked
     * @return true if the account is a local account, false otherwise
     * @deprecated use {@link #isLocalEmail(MailAddress)} instead
     */
    @Deprecated
    boolean isLocalUser(String userAccount);

    /**
     * Checks if an address is local, i.e. its account exists locally
     * and this server is the final delivery destination for messages
     * sent to this address.
     *
     * @param mailAddress the full address of the account to be checked
     * @return true if the account is a local account, false otherwise
     * @since Mailet API 2.4
     */
    boolean isLocalEmail(MailAddress mailAddress);

    default Collection<MailAddress> localRecipients(Collection<MailAddress> recipients) {
        return recipients.stream()
            .filter(this::isLocalEmail)
            .collect(ImmutableList.toImmutableList());
    }

    /**
     * Returns the hostnames that are specified as mail handlers for
     * the given domain name. The host names are determined using DNS
     * lookup of MX records and are returned sorted by priority
     * (as detailed in the SMTP RFC).
     *
     * @param domain the domain name whose mail handling hosts are requested
     * @return the sorted mail-handling hostnames for the domain
     * @deprecated use the generic dnsLookup method
     */
    @Deprecated
    Collection<String> getMailServers(Domain domain);

    /**
     * Returns the SMTP host addresses specified as mail handlers for
     * the given domain name. This is equivalent to calling the
     * {@link #getMailServers} method and then performing address
     * resolution lookups on all returned host names in order.
     * The results are returned as instances of {@link HostAddress}
     * containing the host and address information.
     *
     * @param domain the domain whose mail handling SMTP host addresses are requested
     * @return an Iterator over HostAddress, in proper order of priority, or
     *         an empty iterator if no hosts are found
     * @since Mailet API v2.3
     * @deprecated use the generic dnsLookup method
     */
    @Deprecated
    Iterator<HostAddress> getSMTPHostAddresses(Domain domain);

    /**
     * Sends an outgoing message to the top of this mailet container's root queue.
     * This is functionally equivalent to having opened an SMTP session to the local
     * host and delivering the message using the sender and recipients from within
     * the message itself.
     *
     * @param message the message to send
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void sendMail(MimeMessage message)
            throws MessagingException;

    /**
     * Sends an outgoing message to the top of this mailet container's root queue.
     * This is functionally equivalent to having opened an SMTP session to the local
     * host and delivering the message using the given sender and recipients.
     *
     * @param sender the message sender
     * @param recipients the message recipients as a Collection of MailAddress objects
     * @param message the message to send
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage message)
            throws MessagingException;

    /**
     * Sends an outgoing message to the top of this mailet container's queue for the
     * specified processor.
     *
     * @param sender the message sender
     * @param recipients the message recipients as a Collection of MailAddress objects
     * @param message the message to send
     * @param state the state of the message, indicating the name of the processor for
     *              which the message will be queued
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage message, String state)
            throws MessagingException;

    /**
     * Sends an outgoing message to the top of this mailet container's root queue.
     * This is the equivalent of opening an SMTP session to localhost.
     * The Mail object provides all envelope and content information.
     *
     * @param mail the message that is to sent
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void sendMail(Mail mail) throws MessagingException;

    /**
     * Sends an outgoing message to the top of this mailet container's root queue.
     * This is the equivalent of opening an SMTP session to localhost.
     * The Mail object provides all envelope and content information.
     * <p>
     * The given delay and unit are used to calculate the time when
     * the Mail will be available for deQueue.
     *
     * @param mail the message that is to sent
     * @param delay the delay value for deQueue
     * @param unit the delay unit for deQueue
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void sendMail(Mail mail, long delay, TimeUnit unit) throws MessagingException;


    /**
     * Sends an outgoing message to the top of this mailet container's root queue,
     * targeting a specific processing state.
     * <p>
     * This functionally allows mail treatment done out of the MailetProcessor to be sent
     * to a specific processor inside the MailetContainer. This is for instance useful for bouncing mail
     * being remote delivered (asynchronously to original mail treatment).
     *
     * @param mail The message to send
     * @param state The state of the message, indicating the name of the processor for
     *              which the message will be queued
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void sendMail(Mail mail, String state) throws MessagingException;
    
    /**
     * Sends an outgoing message to the top of this mailet container's root queue,
     * targeting a specific processing state.
     * <p>
     * This functionally allows mail treatment done out of the MailetProcessor to be sent
     * to a specific processor inside the MailetContainer. This is for instance useful for bouncing mail
     * being remote delivered (asynchronously to original mail treatment).
     * <p>
     * The given delay and unit are used to calculate the time when
     * the Mail will be available for deQueue.
     *
     * @param mail The message to send
     * @param state The state of the message, indicating the name of the processor for
     *              which the message will be queued
     * @param delay The delay value for deQueue
     * @param unit The delay unit for deQueue
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void sendMail(Mail mail, String state, long delay, TimeUnit unit) throws MessagingException;

    /**
     * Bounces the message using a standard format with the given message.
     * <p>
     * The message will be sent to the original sender from the postmaster address
     * as configured in this mailet context, adding the message to top of mail
     * server queue using {@code sendMail}.
     *
     * @param mail the message to bounce, with the original sender
     * @param message a descriptive message explaining why the message bounced
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void bounce(Mail mail, String message) throws MessagingException;

    /**
     * Bounces the message using a standard format with the given message.
     * <p/>
     * The message will be sent to the original sender from the given address,
     * adding the message to top of mail server queue using {@code sendMail}.
     *
     * @param mail the message to bounce, with the original sender
     * @param message a descriptive message explaining why the message bounced
     * @param bouncer the address used as the sender of the bounce message
     * @throws MessagingException if an error occurs accessing or sending the message
     */
    void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException;


    /**
     * Looks up the given record type in the DNS system.
     * <p>
     * In the case of MX records the returned List will be sorted using the priority score, ascending.
     *
     * @param name the host/domain name to lookup
     * @param type the "IN" record type to lookup
     * @return a String list with result records with at least 1 element
     * @throws TemporaryLookupException on timeout or servfail
     * @throws LookupException on host not found, record type not found,
     *                         name syntax issues and other permanent exceptions
     * @since Mailet API v2.5
     */
    List<String> dnsLookup(String name, RecordType type) throws TemporaryLookupException, LookupException;

    Logger getLogger();
}
