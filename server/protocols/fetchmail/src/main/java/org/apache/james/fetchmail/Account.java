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

import java.util.ArrayList;
import java.util.List;

import jakarta.mail.Session;
import jakarta.mail.internet.ParseException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.MailAddress;

/**
 * <p>
 * Class <code>Account</code> encapsulates the account details required to fetch
 * mail from a message store.
 * </p>
 * 
 * <p>
 * Instances are <code>Comparable</code> based on their sequence number.
 * </p>
 */
class Account implements Comparable<Account> {
    private static final int DEFAULT_INITIAL_SIZE_OF_DEFERRED_RECIPIENT_ARRAY = 16;

    /**
     * The user password for this account
     */
    private String fieldPassword;

    /**
     * The user to send the fetched mail to
     */
    private MailAddress fieldRecipient;

    /**
     * The user name for this account
     */
    private String fieldUser;

    /**
     * The ParsedConfiguration
     */
    private ParsedConfiguration fieldParsedConfiguration;

    /**
     * List of MessageIDs for which processing has been deferred because the
     * intended recipient could not be found.
     */
    private List<String> fieldDeferredRecipientNotFoundMessageIDs;

    /**
     * The sequence number for this account
     */
    private int fieldSequenceNumber;

    /**
     * Ignore the recipient deduced from the header and use 'fieldRecipient'
     */
    private boolean fieldIgnoreRecipientHeader;

    /**
     * The JavaMail Session for this Account.
     */
    private Session fieldSession;

    /**
     * A custom header to be used as the recipient address
     */
    private String customRecipientHeader;

    /**
     * Constructor for Account.
     */
    private Account() {
        super();
    }

    /**
     * Constructor for Account.
     * 
     * @param sequenceNumber
     * @param parsedConfiguration
     * @param user
     * @param password
     * @param recipient
     * @param ignoreRecipientHeader
     * @param session
     * @throws ConfigurationException
     */

    public Account(int sequenceNumber, ParsedConfiguration parsedConfiguration, String user, String password, String recipient, boolean ignoreRecipientHeader, String customRecipientHeader, Session session) throws ConfigurationException {
        this();
        setSequenceNumber(sequenceNumber);
        setParsedConfiguration(parsedConfiguration);
        setUser(user);
        setPassword(password);
        setRecipient(recipient);
        setIgnoreRecipientHeader(ignoreRecipientHeader);
        setCustomRecipientHeader(customRecipientHeader);
        setSession(session);
    }

    /**
     * Returns the custom recipient header.
     * 
     * @return String
     */
    public String getCustomRecipientHeader() {
        return this.customRecipientHeader;
    }

    /**
     * Returns the password.
     * 
     * @return String
     */
    public String getPassword() {
        return fieldPassword;
    }

    /**
     * Returns the recipient.
     * 
     * @return MailAddress
     */
    public MailAddress getRecipient() {
        return fieldRecipient;
    }

    /**
     * Returns the user.
     * 
     * @return String
     */
    public String getUser() {
        return fieldUser;
    }

    /**
     * Sets the custom recipient header.
     * 
     * @param customRecipientHeader
     *            The header to be used
     */
    public void setCustomRecipientHeader(String customRecipientHeader) {
        this.customRecipientHeader = customRecipientHeader;
    }

    /**
     * Sets the password.
     * 
     * @param password
     *            The password to set
     */
    protected void setPassword(String password) {
        fieldPassword = password;
    }

    /**
     * Sets the recipient.
     * 
     * @param recipient
     *            The recipient to set
     */
    protected void setRecipient(MailAddress recipient) {
        fieldRecipient = recipient;
    }

    /**
     * Sets the recipient.
     * 
     * @param recipient
     *            The recipient to set
     */
    protected void setRecipient(String recipient) throws ConfigurationException {
        if (null == recipient) {
            fieldRecipient = null;
            return;
        }

        try {
            setRecipient(new MailAddress(recipient));
        } catch (ParseException pe) {
            throw new ConfigurationException("Invalid recipient address specified: " + recipient);
        }
    }

    /**
     * Sets the user.
     * 
     * @param user
     *            The user to set
     */
    protected void setUser(String user) {
        fieldUser = user;
    }

    /**
     * Sets the ignoreRecipientHeader.
     * 
     * @param ignoreRecipientHeader
     *            The ignoreRecipientHeader to set
     */
    protected void setIgnoreRecipientHeader(boolean ignoreRecipientHeader) {
        fieldIgnoreRecipientHeader = ignoreRecipientHeader;
    }

    /**
     * Returns the ignoreRecipientHeader.
     * 
     * @return boolean
     */
    public boolean isIgnoreRecipientHeader() {
        return fieldIgnoreRecipientHeader;
    }

    /**
     * Returns the sequenceNumber.
     * 
     * @return int
     */
    public int getSequenceNumber() {
        return fieldSequenceNumber;
    }

    /**
     * Sets the sequenceNumber.
     * 
     * @param sequenceNumber
     *            The sequenceNumber to set
     */
    protected void setSequenceNumber(int sequenceNumber) {
        fieldSequenceNumber = sequenceNumber;
    }

    @Override
    public int compareTo(Account account) {
        return getSequenceNumber() - account.getSequenceNumber();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Account other = (Account) obj;
        return getSequenceNumber() == other.getSequenceNumber();
    }

    @Override
    public int hashCode() {
        return 31 * 17 + getSequenceNumber();
    }

    /**
     * Returns the deferredRecipientNotFoundMessageIDs. lazily initialised.
     * 
     * @return List
     */
    public List<String> getDeferredRecipientNotFoundMessageIDs() {
        List<String> messageIDs;
        if (null == (messageIDs = getDeferredRecipientNotFoundMessageIDsBasic())) {
            updateDeferredRecipientNotFoundMessageIDs();
            return getDeferredRecipientNotFoundMessageIDs();
        }
        return messageIDs;
    }

    /**
     * Returns the deferredRecipientNotFoundMessageIDs.
     * 
     * @return List
     */
    private List<String> getDeferredRecipientNotFoundMessageIDsBasic() {
        return fieldDeferredRecipientNotFoundMessageIDs;
    }

    /**
     * Returns a new List of deferredRecipientNotFoundMessageIDs.
     * 
     * @return List
     */
    protected List<String> computeDeferredRecipientNotFoundMessageIDs() {
        return new ArrayList<>(DEFAULT_INITIAL_SIZE_OF_DEFERRED_RECIPIENT_ARRAY);
    }

    /**
     * Updates the deferredRecipientNotFoundMessageIDs.
     */
    protected void updateDeferredRecipientNotFoundMessageIDs() {
        setDeferredRecipientNotFoundMessageIDs(computeDeferredRecipientNotFoundMessageIDs());
    }

    /**
     * Sets the defferedRecipientNotFoundMessageIDs.
     * 
     * @param defferedRecipientNotFoundMessageIDs
     *            The defferedRecipientNotFoundMessageIDs to set
     */
    protected void setDeferredRecipientNotFoundMessageIDs(List<String> defferedRecipientNotFoundMessageIDs) {
        fieldDeferredRecipientNotFoundMessageIDs = defferedRecipientNotFoundMessageIDs;
    }

    /**
     * Returns the parsedConfiguration.
     * 
     * @return ParsedConfiguration
     */
    public ParsedConfiguration getParsedConfiguration() {
        return fieldParsedConfiguration;
    }

    /**
     * Sets the parsedConfiguration.
     * 
     * @param parsedConfiguration
     *            The parsedConfiguration to set
     */
    protected void setParsedConfiguration(ParsedConfiguration parsedConfiguration) {
        fieldParsedConfiguration = parsedConfiguration;
    }

    /**
     * Returns the session.
     * 
     * @return Session
     */
    public Session getSession() {
        return fieldSession;
    }

    /**
     * Sets the session.
     * 
     * @param session
     *            The session to set
     */
    protected void setSession(Session session) {
        fieldSession = session;
    }

}
