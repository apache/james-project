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
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import jakarta.mail.internet.ParseException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.user.api.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Parses and validates an
 * <code>org.apache.avalon.framework.configuration.Configuration</code>.
 * </p>
 */
class ParsedConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParsedConfiguration.class);

    /**
     * The name of the folder to fetch from the javamail provider
     * 
     */
    private String fieldJavaMailFolderName = "INBOX";

    /**
     * The name of the javamail provider we want to user (pop3,imap,nntp,etc...)
     * 
     */
    private String fieldJavaMailProviderName = "pop3";

    /**
     * Returns the javaMailFolderName.
     * 
     * @return String
     */
    public String getJavaMailFolderName() {
        return fieldJavaMailFolderName;
    }

    /**
     * Returns the javaMailProviderName.
     * 
     * @return String
     */
    public String getJavaMailProviderName() {
        return fieldJavaMailProviderName;
    }

    /**
     * Sets the javaMailFolderName.
     * 
     * @param javaMailFolderName
     *            The javaMailFolderName to set
     */
    protected void setJavaMailFolderName(String javaMailFolderName) {
        fieldJavaMailFolderName = javaMailFolderName;
    }

    /**
     * Sets the javaMailProviderName.
     * 
     * @param javaMailProviderName
     *            The javaMailProviderName to set
     */
    protected void setJavaMailProviderName(String javaMailProviderName) {
        fieldJavaMailProviderName = javaMailProviderName;
    }

    /**
     * Fetch both old (seen) and new messages from the mailserver. The default
     * is to fetch only messages the server are not marked as seen.
     */
    private boolean fieldFetchAll = false;

    /**
     * The unique, identifying name for this task
     */
    private String fieldFetchTaskName;

    /**
     * The server host name for this fetch task
     */
    private String fieldHost;

    /**
     * Keep retrieved messages on the remote mailserver. Normally, messages are
     * deleted from the folder on the mailserver after they have been retrieved
     */
    private boolean fieldLeave = false;

    /**
     * Keep blacklisted messages on the remote mailserver. Normally, messages
     * are kept in the folder on the mailserver if they have been rejected
     */
    private boolean fieldLeaveBlacklisted = true;

    /**
     * Keep messages for remote recipients on the remote mailserver. Normally,
     * messages are kept in the folder on the mailserver if they have been
     * rejected.
     */
    private boolean fieldLeaveRemoteRecipient = true;

    /**
     * Keep messages for undefined users on the remote mailserver. Normally,
     * messages are kept in the folder on the mailserver if they have been
     * rejected.
     */
    private boolean fieldLeaveUserUndefined = true;

    /**
     * Keep undeliverable messages on the remote mailserver. Normally, messages
     * are kept in the folder on the mailserver if they cannot be delivered.
     */
    private boolean fieldLeaveUndeliverable = true;

    /**
     * Mark retrieved messages on the remote mailserver as seen. Normally,
     * messages are marked as seen after they have been retrieved
     */
    private boolean fieldMarkSeen = true;

    /**
     * Mark blacklisted messages on the remote mailserver as seen. Normally,
     * messages are not marked as seen if they have been rejected
     */
    private boolean fieldMarkBlacklistedSeen = false;

    /**
     * Mark remote recipient messages on the remote mailserver as seen.
     * Normally, messages are not marked as seen if they have been rejected
     */
    private boolean fieldMarkRemoteRecipientSeen = false;

    /**
     * Mark messages for undefined users on the remote mail server as seen.
     * Normally, messages are not marked as seen if they have been rejected.
     */
    private boolean fieldMarkUserUndefinedSeen = false;

    /**
     * Mark undeliverable messages on the remote mail server as seen. Normally,
     * messages are not marked as seen if they are undeliverable.
     */
    private boolean fieldMarkUndeliverableSeen = false;

    /**
     * Defer processing of messages for which the intended recipient cannot be
     * determined to the next pass.
     */
    private boolean fieldDeferRecipientNotFound = false;

    /**
     * Recurse folders if available?
     */
    private boolean fieldRecurse = false;

    /**
     * The domain part to use to complete partial addresses
     */
    private String fieldDefaultDomainName;

    /**
     * Only accept mail for defined recipients. All other mail is rejected.
     */
    private boolean fieldRejectUserUndefined;

    /**
     * The index of the received header to use to compute the remote address and
     * remote host name for a message. This is 0 based and defaults to -1.
     */
    private int fieldRemoteReceivedHeaderIndex = -1;

    /**
     * Keep messages with an invalid received header on the remote mailserver.
     * Normally, messages are kept in the folder on the mailserver if they have
     * been rejected
     */
    private boolean fieldLeaveRemoteReceivedHeaderInvalid = true;

    /**
     * Mark messages with an invalid received header on the remote mailserver as
     * seen. Normally, messages are not marked as seen if they have been
     * rejected.
     */
    private boolean fieldMarkRemoteReceivedHeaderInvalidSeen = false;

    /**
     * Reject messages with an invalid received header.
     */
    private boolean fieldRejectRemoteReceivedHeaderInvalid;

    /**
     * Reject messages for which a recipient could not be determined.
     */
    private boolean fieldRejectRecipientNotFound;

    /**
     * Leave messages on the server for which a recipient could not be
     * determined.
     */
    private boolean fieldLeaveRecipientNotFound;

    /**
     * Mark as seen messages on the server for which a recipient could not be
     * determined.
     */
    private boolean fieldMarkRecipientNotFoundSeen;

    /**
     * Reject mail for blacklisted users
     */
    private boolean fieldRejectBlacklisted;

    /**
     * Only accept mail for local recipients. All other mail is rejected.
     */
    private boolean fieldRejectRemoteRecipient;

    /**
     * The Set of MailAddresses for whom mail should be rejected
     */
    private Set<MailAddress> fieldBlacklist;

    /**
     * The maximum message size limit 0 means no limit.
     */
    private int fieldMaxMessageSizeLimit = 0;

    /**
     * Reject mail exceeding the maximum message size limit
     */
    private boolean fieldRejectMaxMessageSizeExceeded;

    /**
     * Leave messages on the server that exceed the maximum message size limit.
     */
    private boolean fieldLeaveMaxMessageSizeExceeded;

    /**
     * Mark as seen messages on the server that exceed the maximum message size
     * limit.
     */
    private boolean fieldMarkMaxMessageSizeExceededSeen;

    /**
     * The Local Users repository
     */
    private UsersRepository fieldLocalUsers;

    /**
     * The DNSService
     */
    private DNSService dnsServer;

    private MailQueue queue;

    private DomainList domainList;

    /**
     * Constructor for ParsedConfiguration.
     */
    private ParsedConfiguration() {
        super();
    }

    /**
     * Constructor for ParsedConfiguration.
     * 
     * @param configuration
     * @param localUsers
     * @param dnsServer
     * @throws ConfigurationException
     */
    public ParsedConfiguration(HierarchicalConfiguration<ImmutableNode> configuration, UsersRepository localUsers, DNSService dnsServer, DomainList domainList, MailQueue queue) throws ConfigurationException {
        this();
        setLocalUsers(localUsers);
        setDNSServer(dnsServer);
        setDomainList(domainList);
        setMailQueue(queue);
        configure(configuration);
    }

    protected void configure(HierarchicalConfiguration<ImmutableNode> conf) throws ConfigurationException {
        setHost(conf.getString("host"));

        setFetchTaskName(conf.getString("[@name]"));
        setJavaMailProviderName(conf.getString("javaMailProviderName"));
        setJavaMailFolderName(conf.getString("javaMailFolderName"));
        setRecurse(conf.getBoolean("recursesubfolders"));

        HierarchicalConfiguration<ImmutableNode> recipientNotFound = conf.configurationAt("recipientnotfound");
        setDeferRecipientNotFound(recipientNotFound.getBoolean("[@defer]"));
        setRejectRecipientNotFound(recipientNotFound.getBoolean("[@reject]"));
        setLeaveRecipientNotFound(recipientNotFound.getBoolean("[@leaveonserver]"));
        setMarkRecipientNotFoundSeen(recipientNotFound.getBoolean("[@markseen]"));
        setDefaultDomainName(conf.getString("defaultdomain", "localhost"));

        setFetchAll(conf.getBoolean("fetchall"));

        HierarchicalConfiguration<ImmutableNode> fetched = conf.configurationAt("fetched");
        setLeave(fetched.getBoolean("[@leaveonserver]"));
        setMarkSeen(fetched.getBoolean("[@markseen]"));

        HierarchicalConfiguration<ImmutableNode> remoterecipient = conf.configurationAt("remoterecipient");
        setRejectRemoteRecipient(remoterecipient.getBoolean("[@reject]"));
        setLeaveRemoteRecipient(remoterecipient.getBoolean("[@leaveonserver]"));
        setMarkRemoteRecipientSeen(remoterecipient.getBoolean("[@markseen]"));

        HierarchicalConfiguration<ImmutableNode> blacklist = conf.configurationAt("blacklist");
        setBlacklist(conf.getString("blacklist", ""));
        setRejectBlacklisted(blacklist.getBoolean("[@reject]"));
        setLeaveBlacklisted(blacklist.getBoolean("[@leaveonserver]"));
        setMarkBlacklistedSeen(blacklist.getBoolean("[@markseen]"));

        HierarchicalConfiguration<ImmutableNode> userundefined = conf.configurationAt("userundefined");
        setRejectUserUndefined(userundefined.getBoolean("[@reject]"));
        setLeaveUserUndefined(userundefined.getBoolean("[@leaveonserver]"));
        setMarkUserUndefinedSeen(userundefined.getBoolean("[@markseen]"));

        HierarchicalConfiguration<ImmutableNode> undeliverable = conf.configurationAt("undeliverable");
        setLeaveUndeliverable(undeliverable.getBoolean("[@leaveonserver]"));
        setMarkUndeliverableSeen(undeliverable.getBoolean("[@markseen]"));

        if (conf.getKeys("remotereceivedheader").hasNext()) {
            HierarchicalConfiguration<ImmutableNode> remotereceivedheader = conf.configurationAt("remotereceivedheader");

            setRemoteReceivedHeaderIndex(remotereceivedheader.getInt("[@index]"));
            setRejectRemoteReceivedHeaderInvalid(remotereceivedheader.getBoolean("[@reject]"));
            setLeaveRemoteReceivedHeaderInvalid(remotereceivedheader.getBoolean("[@leaveonserver]"));
            setMarkRemoteReceivedHeaderInvalidSeen(remotereceivedheader.getBoolean("[@markseen]"));
        }

        if (conf.getKeys("maxmessagesize").hasNext()) {
            HierarchicalConfiguration<ImmutableNode> maxmessagesize = conf.configurationAt("maxmessagesize");

            setMaxMessageSizeLimit(maxmessagesize.getInt("[@limit]") * 1024);
            setRejectMaxMessageSizeExceeded(maxmessagesize.getBoolean("[@reject]"));
            setLeaveMaxMessageSizeExceeded(maxmessagesize.getBoolean("[@leaveonserver]"));
            setMarkMaxMessageSizeExceededSeen(maxmessagesize.getBoolean("[@markseen]"));
        }

        LOGGER.info("Configured FetchMail fetch task {}", getFetchTaskName());
    }

    /**
     * Returns the fetchAll.
     * 
     * @return boolean
     */
    public boolean isFetchAll() {
        return fieldFetchAll;
    }

    /**
     * Returns the fetchTaskName.
     * 
     * @return String
     */
    public String getFetchTaskName() {
        return fieldFetchTaskName;
    }

    /**
     * Returns the host.
     * 
     * @return String
     */
    public String getHost() {
        return fieldHost;
    }

    /**
     * Returns the keep.
     * 
     * @return boolean
     */
    public boolean isLeave() {
        return fieldLeave;
    }

    /**
     * Returns the markSeen.
     * 
     * @return boolean
     */
    public boolean isMarkSeen() {
        return fieldMarkSeen;
    }

    /**
     * Answers true if the folder should be opened read only. For this to be
     * true the configuration options must not require folder updates.
     * 
     * @return boolean
     */
    protected boolean isOpenReadOnly() {
        return isLeave() && !isMarkSeen() && isLeaveBlacklisted() && !isMarkBlacklistedSeen() && isLeaveRemoteRecipient() && !isMarkRemoteRecipientSeen() && isLeaveUserUndefined() && !isMarkUserUndefinedSeen() && isLeaveUndeliverable() && !isMarkUndeliverableSeen()
                && isLeaveMaxMessageSizeExceeded() && !isMarkMaxMessageSizeExceededSeen() && isLeaveRemoteReceivedHeaderInvalid() && !isMarkRemoteReceivedHeaderInvalidSeen();
    }

    /**
     * Returns the recurse.
     * 
     * @return boolean
     */
    public boolean isRecurse() {
        return fieldRecurse;
    }

    /**
     * Sets the fetchAll.
     * 
     * @param fetchAll
     *            The fetchAll to set
     */
    protected void setFetchAll(boolean fetchAll) {
        fieldFetchAll = fetchAll;
    }

    /**
     * Sets the fetchTaskName.
     * 
     * @param fetchTaskName
     *            The fetchTaskName to set
     */
    protected void setFetchTaskName(String fetchTaskName) {
        fieldFetchTaskName = fetchTaskName;
    }

    /**
     * Sets the host.
     * 
     * @param host
     *            The host to set
     */
    protected void setHost(String host) {
        fieldHost = host;
    }

    /**
     * Sets the keep.
     * 
     * @param keep
     *            The keep to set
     */
    protected void setLeave(boolean keep) {
        fieldLeave = keep;
    }

    /**
     * Sets the markSeen.
     * 
     * @param markSeen
     *            The markSeen to set
     */
    protected void setMarkSeen(boolean markSeen) {
        fieldMarkSeen = markSeen;
    }

    /**
     * Sets the recurse.
     * 
     * @param recurse
     *            The recurse to set
     */
    protected void setRecurse(boolean recurse) {
        fieldRecurse = recurse;
    }

    /**
     * Returns the localUsers.
     * 
     * @return UsersRepository
     */
    public UsersRepository getLocalUsers() {
        return fieldLocalUsers;
    }

    /**
     * Sets the localUsers.
     * 
     * @param localUsers
     *            The localUsers to set
     */
    protected void setLocalUsers(UsersRepository localUsers) {
        fieldLocalUsers = localUsers;
    }

    /**
     * Return the DNSService
     * 
     * @return dnsServer The DNSService
     */
    public DNSService getDNSServer() {
        return dnsServer;
    }

    /**
     * Set the DNSService
     * 
     * @param dnsServer
     *            The dnsServer to use
     */
    protected void setDNSServer(DNSService dnsServer) {
        this.dnsServer = dnsServer;
    }

    /**
     * Returns the keepRejected.
     * 
     * @return boolean
     */
    public boolean isLeaveBlacklisted() {
        return fieldLeaveBlacklisted;
    }

    /**
     * Returns the markRejectedSeen.
     * 
     * @return boolean
     */
    public boolean isMarkBlacklistedSeen() {
        return fieldMarkBlacklistedSeen;
    }

    /**
     * Sets the keepRejected.
     * 
     * @param keepRejected
     *            The keepRejected to set
     */
    protected void setLeaveBlacklisted(boolean keepRejected) {
        fieldLeaveBlacklisted = keepRejected;
    }

    /**
     * Sets the markRejectedSeen.
     * 
     * @param markRejectedSeen
     *            The markRejectedSeen to set
     */
    protected void setMarkBlacklistedSeen(boolean markRejectedSeen) {
        fieldMarkBlacklistedSeen = markRejectedSeen;
    }

    /**
     * Returns the blacklist.
     * 
     * @return Set
     */
    public Set<MailAddress> getBlacklist() {
        return fieldBlacklist;
    }

    /**
     * Sets the blacklist.
     * 
     * @param blacklist
     *            The blacklist to set
     */
    protected void setBlacklist(Set<MailAddress> blacklist) {
        fieldBlacklist = blacklist;
    }

    /**
     * Sets the blacklist.
     * 
     * @param blacklistValue
     *            The blacklist to set
     */
    protected void setBlacklist(String blacklistValue) throws ConfigurationException {
        StringTokenizer st = new StringTokenizer(blacklistValue, ", \t", false);
        Set<MailAddress> blacklist = new HashSet<>();
        String token = null;
        while (st.hasMoreTokens()) {
            try {
                token = st.nextToken();
                blacklist.add(new MailAddress(token));
            } catch (ParseException pe) {
                throw new ConfigurationException("Invalid blacklist mail address specified: " + token);
            }
        }
        setBlacklist(blacklist);
    }

    /**
     * Returns the localRecipientsOnly.
     * 
     * @return boolean
     */
    public boolean isRejectUserUndefined() {
        return fieldRejectUserUndefined;
    }

    /**
     * Sets the localRecipientsOnly.
     * 
     * @param localRecipientsOnly
     *            The localRecipientsOnly to set
     */
    protected void setRejectUserUndefined(boolean localRecipientsOnly) {
        fieldRejectUserUndefined = localRecipientsOnly;
    }

    /**
     * Returns the markExternalSeen.
     * 
     * @return boolean
     */
    public boolean isMarkUserUndefinedSeen() {
        return fieldMarkUserUndefinedSeen;
    }

    /**
     * Sets the markExternalSeen.
     * 
     * @param markExternalSeen
     *            The markExternalSeen to set
     */
    protected void setMarkUserUndefinedSeen(boolean markExternalSeen) {
        fieldMarkUserUndefinedSeen = markExternalSeen;
    }

    /**
     * Returns the leaveExternal.
     * 
     * @return boolean
     */
    public boolean isLeaveUserUndefined() {
        return fieldLeaveUserUndefined;
    }

    /**
     * Sets the leaveExternal.
     * 
     * @param leaveExternal
     *            The leaveExternal to set
     */
    protected void setLeaveUserUndefined(boolean leaveExternal) {
        fieldLeaveUserUndefined = leaveExternal;
    }

    /**
     * Returns the leaveRemoteRecipient.
     * 
     * @return boolean
     */
    public boolean isLeaveRemoteRecipient() {
        return fieldLeaveRemoteRecipient;
    }

    /**
     * Returns the markRemoteRecipientSeen.
     * 
     * @return boolean
     */
    public boolean isMarkRemoteRecipientSeen() {
        return fieldMarkRemoteRecipientSeen;
    }

    /**
     * Sets the leaveRemoteRecipient.
     * 
     * @param leaveRemoteRecipient
     *            The leaveRemoteRecipient to set
     */
    protected void setLeaveRemoteRecipient(boolean leaveRemoteRecipient) {
        fieldLeaveRemoteRecipient = leaveRemoteRecipient;
    }

    /**
     * Sets the markRemoteRecipientSeen.
     * 
     * @param markRemoteRecipientSeen
     *            The markRemoteRecipientSeen to set
     */
    protected void setMarkRemoteRecipientSeen(boolean markRemoteRecipientSeen) {
        fieldMarkRemoteRecipientSeen = markRemoteRecipientSeen;
    }

    /**
     * Returns the rejectRemoteRecipient.
     * 
     * @return boolean
     */
    public boolean isRejectRemoteRecipient() {
        return fieldRejectRemoteRecipient;
    }

    /**
     * Sets the rejectRemoteRecipient.
     * 
     * @param rejectRemoteRecipient
     *            The rejectRemoteRecipient to set
     */
    protected void setRejectRemoteRecipient(boolean rejectRemoteRecipient) {
        fieldRejectRemoteRecipient = rejectRemoteRecipient;
    }

    /**
     * Returns the defaultDomainName. Lazy initializes if required.
     * 
     * @return String
     */
    public String getDefaultDomainName() {
        String defaultDomainName;
        if (null == (defaultDomainName = getDefaultDomainNameBasic())) {
            updateDefaultDomainName();
            return getDefaultDomainName();
        }
        return defaultDomainName;
    }

    /**
     * Returns the defaultDomainName.
     * 
     * @return String
     */
    private String getDefaultDomainNameBasic() {
        return fieldDefaultDomainName;
    }

    /**
     * Validates and sets the defaultDomainName.
     * 
     * @param defaultDomainName
     *            The defaultDomainName to set
     */
    protected void setDefaultDomainName(String defaultDomainName) throws ConfigurationException {
        validateDefaultDomainName(defaultDomainName);
        setDefaultDomainNameBasic(defaultDomainName);
    }

    /**
     * Sets the defaultDomainName.
     * 
     * @param defaultDomainName
     *            The defaultDomainName to set
     */
    private void setDefaultDomainNameBasic(String defaultDomainName) {
        fieldDefaultDomainName = defaultDomainName;
    }

    /**
     * Validates the defaultDomainName.
     * 
     * @param defaultDomainName
     *            The defaultDomainName to validate
     */
    protected void validateDefaultDomainName(String defaultDomainName) throws ConfigurationException {
        try {
            if (!getDomainList().containsDomain(Domain.of(defaultDomainName))) {
                throw new ConfigurationException("Default domain name is not a local server: " + defaultDomainName);
            }
        } catch (DomainListException e) {
            throw new ConfigurationException("Unable to access DomainList", e);
        }
    }

    /**
     * Computes the defaultDomainName.
     */
    protected String computeDefaultDomainName() {
        String hostName;
        try {
            hostName = getDNSServer().getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException ue) {
            hostName = "localhost";
        }
        return hostName;
    }

    /**
     * Updates the defaultDomainName.
     */
    protected void updateDefaultDomainName() {
        setDefaultDomainNameBasic(computeDefaultDomainName());
    }

    /**
     * Returns the leaveUndeliverable.
     * 
     * @return boolean
     */
    public boolean isLeaveUndeliverable() {
        return fieldLeaveUndeliverable;
    }

    /**
     * Returns the markUndeliverableSeen.
     * 
     * @return boolean
     */
    public boolean isMarkUndeliverableSeen() {
        return fieldMarkUndeliverableSeen;
    }

    /**
     * Sets the leaveUndeliverable.
     * 
     * @param leaveUndeliverable
     *            The leaveUndeliverable to set
     */
    protected void setLeaveUndeliverable(boolean leaveUndeliverable) {
        fieldLeaveUndeliverable = leaveUndeliverable;
    }

    /**
     * Sets the markUndeliverableSeen.
     * 
     * @param markUndeliverableSeen
     *            The markUndeliverableSeen to set
     */
    protected void setMarkUndeliverableSeen(boolean markUndeliverableSeen) {
        fieldMarkUndeliverableSeen = markUndeliverableSeen;
    }

    /**
     * Returns the rejectBlacklisted.
     * 
     * @return boolean
     */
    public boolean isRejectBlacklisted() {
        return fieldRejectBlacklisted;
    }

    /**
     * Sets the rejectBlacklisted.
     * 
     * @param rejectBlacklisted
     *            The rejectBlacklisted to set
     */
    protected void setRejectBlacklisted(boolean rejectBlacklisted) {
        fieldRejectBlacklisted = rejectBlacklisted;
    }

    /**
     * Returns the leaveRecipientNotFound.
     * 
     * @return boolean
     */
    public boolean isLeaveRecipientNotFound() {
        return fieldLeaveRecipientNotFound;
    }

    /**
     * Returns the markRecipientNotFoundSeen.
     * 
     * @return boolean
     */
    public boolean isMarkRecipientNotFoundSeen() {
        return fieldMarkRecipientNotFoundSeen;
    }

    /**
     * Returns the rejectRecipientNotFound.
     * 
     * @return boolean
     */
    public boolean isRejectRecipientNotFound() {
        return fieldRejectRecipientNotFound;
    }

    /**
     * Sets the leaveRecipientNotFound.
     * 
     * @param leaveRecipientNotFound
     *            The leaveRecipientNotFound to set
     */
    protected void setLeaveRecipientNotFound(boolean leaveRecipientNotFound) {
        fieldLeaveRecipientNotFound = leaveRecipientNotFound;
    }

    /**
     * Sets the markRecipientNotFoundSeen.
     * 
     * @param markRecipientNotFoundSeen
     *            The markRecipientNotFoundSeen to set
     */
    protected void setMarkRecipientNotFoundSeen(boolean markRecipientNotFoundSeen) {
        fieldMarkRecipientNotFoundSeen = markRecipientNotFoundSeen;
    }

    /**
     * Sets the rejectRecipientNotFound.
     * 
     * @param rejectRecipientNotFound
     *            The rejectRecipientNotFound to set
     */
    protected void setRejectRecipientNotFound(boolean rejectRecipientNotFound) {
        fieldRejectRecipientNotFound = rejectRecipientNotFound;
    }

    /**
     * Returns the deferRecipientNotFound.
     * 
     * @return boolean
     */
    public boolean isDeferRecipientNotFound() {
        return fieldDeferRecipientNotFound;
    }

    /**
     * Sets the deferRecipientNotFound.
     * 
     * @param deferRecipientNotFound
     *            The deferRecepientNotFound to set
     */
    protected void setDeferRecipientNotFound(boolean deferRecipientNotFound) {
        fieldDeferRecipientNotFound = deferRecipientNotFound;
    }

    /**
     * Returns the remoteReceivedHeaderIndex.
     * 
     * @return int
     */
    public int getRemoteReceivedHeaderIndex() {
        return fieldRemoteReceivedHeaderIndex;
    }

    /**
     * Sets the remoteReceivedHeaderIndex.
     * 
     * @param remoteReceivedHeaderIndex
     *            The remoteReceivedHeaderIndex to set
     */
    protected void setRemoteReceivedHeaderIndex(int remoteReceivedHeaderIndex) {
        fieldRemoteReceivedHeaderIndex = remoteReceivedHeaderIndex;
    }

    /**
     * Returns the leaveMaxMessageSize.
     * 
     * @return boolean
     */
    public boolean isLeaveMaxMessageSizeExceeded() {
        return fieldLeaveMaxMessageSizeExceeded;
    }

    /**
     * Returns the markMaxMessageSizeSeen.
     * 
     * @return boolean
     */
    public boolean isMarkMaxMessageSizeExceededSeen() {
        return fieldMarkMaxMessageSizeExceededSeen;
    }

    /**
     * Returns the maxMessageSizeLimit.
     * 
     * @return int
     */
    public int getMaxMessageSizeLimit() {
        return fieldMaxMessageSizeLimit;
    }

    /**
     * Returns the rejectMaxMessageSize.
     * 
     * @return boolean
     */
    public boolean isRejectMaxMessageSizeExceeded() {
        return fieldRejectMaxMessageSizeExceeded;
    }

    /**
     * Sets the leaveMaxMessageSize.
     * 
     * @param leaveMaxMessageSize
     *            The leaveMaxMessageSize to set
     */
    protected void setLeaveMaxMessageSizeExceeded(boolean leaveMaxMessageSize) {
        fieldLeaveMaxMessageSizeExceeded = leaveMaxMessageSize;
    }

    /**
     * Sets the markMaxMessageSizeSeen.
     * 
     * @param markMaxMessageSizeSeen
     *            The markMaxMessageSizeSeen to set
     */
    protected void setMarkMaxMessageSizeExceededSeen(boolean markMaxMessageSizeSeen) {
        fieldMarkMaxMessageSizeExceededSeen = markMaxMessageSizeSeen;
    }

    /**
     * Sets the maxMessageSizeLimit.
     * 
     * @param maxMessageSizeLimit
     *            The maxMessageSizeLimit to set
     */
    protected void setMaxMessageSizeLimit(int maxMessageSizeLimit) {
        fieldMaxMessageSizeLimit = maxMessageSizeLimit;
    }

    /**
     * Sets the rejectMaxMessageSize.
     * 
     * @param rejectMaxMessageSize
     *            The rejectMaxMessageSize to set
     */
    protected void setRejectMaxMessageSizeExceeded(boolean rejectMaxMessageSize) {
        fieldRejectMaxMessageSizeExceeded = rejectMaxMessageSize;
    }

    /**
     * Returns the leaveRemoteReceivedHeaderInvalid.
     * 
     * @return boolean
     */
    public boolean isLeaveRemoteReceivedHeaderInvalid() {
        return fieldLeaveRemoteReceivedHeaderInvalid;
    }

    /**
     * Returns the markRemoteReceivedHeaderInvalidSeen.
     * 
     * @return boolean
     */
    public boolean isMarkRemoteReceivedHeaderInvalidSeen() {
        return fieldMarkRemoteReceivedHeaderInvalidSeen;
    }

    /**
     * Returns the rejectRemoteReceivedHeaderInvalid.
     * 
     * @return boolean
     */
    public boolean isRejectRemoteReceivedHeaderInvalid() {
        return fieldRejectRemoteReceivedHeaderInvalid;
    }

    /**
     * Sets the leaveRemoteReceivedHeaderInvalid.
     * 
     * @param leaveRemoteReceivedHeaderInvalid
     *            The leaveRemoteReceivedHeaderInvalid to set
     */
    protected void setLeaveRemoteReceivedHeaderInvalid(boolean leaveRemoteReceivedHeaderInvalid) {
        fieldLeaveRemoteReceivedHeaderInvalid = leaveRemoteReceivedHeaderInvalid;
    }

    /**
     * Sets the markRemoteReceivedHeaderInvalidSeen.
     * 
     * @param markRemoteReceivedHeaderInvalidSeen
     *            The markRemoteReceivedHeaderInvalidSeen to set
     */
    protected void setMarkRemoteReceivedHeaderInvalidSeen(boolean markRemoteReceivedHeaderInvalidSeen) {
        fieldMarkRemoteReceivedHeaderInvalidSeen = markRemoteReceivedHeaderInvalidSeen;
    }

    /**
     * Sets the rejectRemoteReceivedHeaderInvalid.
     * 
     * @param rejectRemoteReceivedHeaderInvalid
     *            The rejectRemoteReceivedHeaderInvalid to set
     */
    protected void setRejectRemoteReceivedHeaderInvalid(boolean rejectRemoteReceivedHeaderInvalid) {
        fieldRejectRemoteReceivedHeaderInvalid = rejectRemoteReceivedHeaderInvalid;
    }

    public void setMailQueue(MailQueue queue) {
        this.queue = queue;
    }

    public MailQueue getMailQueue() {
        return queue;
    }

    public DomainList getDomainList() {
        return domainList;
    }

    public void setDomainList(DomainList domainList) {
        this.domainList = domainList;
    }
}
