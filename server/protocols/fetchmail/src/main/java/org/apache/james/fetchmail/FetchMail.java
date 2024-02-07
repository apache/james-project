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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Class <code>FetchMail</code> is an Avalon task that is periodically triggered
 * to fetch mail from a JavaMail Message Store.
 * </p>
 * <p/>
 * <p>
 * The lifecycle of an instance of <code>FetchMail</code> is managed by Avalon.
 * The <code>configure(Configuration)</code> method is invoked to parse and
 * validate Configuration properties. The targetTriggered(String) method is
 * invoked to execute the task.
 * </p>
 * <p/>
 * <p>
 * When triggered, a sorted list of Message Store Accounts to be processed is
 * built. Each Message Store Account is processed by delegating to
 * <code>StoreProcessor</code>.
 * </p>
 * <p/>
 * <p>
 * There are two kinds of Message Store Accounts, static and dynamic. Static
 * accounts are explicitly declared in the Configuration. Dynamic accounts are
 * built each time the task is executed, one per each user defined to James,
 * using the James user name with a configurable prefix and suffix to define the
 * host user identity and recipient identity for each Account. Dynamic accounts
 * allow <code>FetchMail</code> to fetch mail for all James users without
 * modifying the Configuration parameters or restarting the Avalon server.
 * </p>
 * <p/>
 * <p>
 * To fully understand the operations supported by this task, read the Class
 * documentation for each Class in the delegation chain starting with this class'
 * delegate, <code>StoreProcessor</code>.
 * </p>
 */
public class FetchMail implements Runnable, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchMail.class);

    /**
     * Key fields for DynamicAccounts.
     */
    private static final class DynamicAccountKey {
        /**
         * The base user name without prfix or suffix
         */
        private String fieldUserName;

        /**
         * The sequence number of the parameters used to construct the Account
         */
        private int fieldSequenceNumber;

        /**
         * Constructor for DynamicAccountKey.
         */
        private DynamicAccountKey() {
            super();
        }

        /**
         * Constructor for DynamicAccountKey.
         */
        public DynamicAccountKey(String userName, int sequenceNumber) {
            this();
            setUserName(userName);
            setSequenceNumber(sequenceNumber);
        }

        @Override
        public boolean equals(Object obj) {
            return null != obj && obj.getClass() == getClass() && (getUserName().equals(((DynamicAccountKey) obj).getUserName()) && getSequenceNumber() == ((DynamicAccountKey) obj).getSequenceNumber());
        }

        @Override
        public int hashCode() {
            return getUserName().hashCode() ^ getSequenceNumber();
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
         * Returns the userName.
         *
         * @return String
         */
        public String getUserName() {
            return fieldUserName;
        }

        /**
         * Sets the sequenceNumber.
         *
         * @param sequenceNumber The sequenceNumber to set
         */
        protected void setSequenceNumber(int sequenceNumber) {
            fieldSequenceNumber = sequenceNumber;
        }

        /**
         * Sets the userName.
         *
         * @param userName The userName to set
         */
        protected void setUserName(String userName) {
            fieldUserName = userName;
        }

    }

    private static final class ParsedDynamicAccountParameters {
        private String fieldUserPrefix;
        private String fieldUserSuffix;

        private String fieldPassword;

        private int fieldSequenceNumber;

        private boolean fieldIgnoreRecipientHeader;
        private String fieldRecipientPrefix;
        private String fieldRecipientSuffix;
        private String customRecipientHeader;

        /**
         * Constructor for ParsedDynamicAccountParameters.
         */
        private ParsedDynamicAccountParameters() {
            super();
        }

        /**
         * Constructor for ParsedDynamicAccountParameters.
         */
        public ParsedDynamicAccountParameters(int sequenceNumber, Configuration configuration) {
            this();
            setSequenceNumber(sequenceNumber);
            setUserPrefix(configuration.getString("[@userprefix]", ""));
            setUserSuffix(configuration.getString("[@usersuffix]", ""));
            setRecipientPrefix(configuration.getString("[@recipientprefix]", ""));
            setRecipientSuffix(configuration.getString("[@recipientsuffix]", ""));
            setPassword(configuration.getString("[@password]"));
            setIgnoreRecipientHeader(configuration.getBoolean("[@ignorercpt-header]"));
            setCustomRecipientHeader(configuration.getString("[@customrcpt-header]", ""));
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
         * Returns the recipientprefix.
         *
         * @return String
         */
        public String getRecipientPrefix() {
            return fieldRecipientPrefix;
        }

        /**
         * Returns the recipientsuffix.
         *
         * @return String
         */
        public String getRecipientSuffix() {
            return fieldRecipientSuffix;
        }

        /**
         * Returns the userprefix.
         *
         * @return String
         */
        public String getUserPrefix() {
            return fieldUserPrefix;
        }

        /**
         * Returns the userSuffix.
         *
         * @return String
         */
        public String getUserSuffix() {
            return fieldUserSuffix;
        }

        /**
         * Sets the custom recipient header.
         *
         * @param customRecipientHeader The header to be used
         */
        public void setCustomRecipientHeader(String customRecipientHeader) {
            this.customRecipientHeader = customRecipientHeader;
        }

        /**
         * Sets the recipientprefix.
         *
         * @param recipientprefix The recipientprefix to set
         */
        protected void setRecipientPrefix(String recipientprefix) {
            fieldRecipientPrefix = recipientprefix;
        }

        /**
         * Sets the recipientsuffix.
         *
         * @param recipientsuffix The recipientsuffix to set
         */
        protected void setRecipientSuffix(String recipientsuffix) {
            fieldRecipientSuffix = recipientsuffix;
        }

        /**
         * Sets the userprefix.
         *
         * @param userprefix The userprefix to set
         */
        protected void setUserPrefix(String userprefix) {
            fieldUserPrefix = userprefix;
        }

        /**
         * Sets the userSuffix.
         *
         * @param userSuffix The userSuffix to set
         */
        protected void setUserSuffix(String userSuffix) {
            fieldUserSuffix = userSuffix;
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
         * Sets the ignoreRecipientHeader.
         *
         * @param ignoreRecipientHeader The ignoreRecipientHeader to set
         */
        protected void setIgnoreRecipientHeader(boolean ignoreRecipientHeader) {
            fieldIgnoreRecipientHeader = ignoreRecipientHeader;
        }

        /**
         * Sets the password.
         *
         * @param password The password to set
         */
        protected void setPassword(String password) {
            fieldPassword = password;
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
         * @param sequenceNumber The sequenceNumber to set
         */
        protected void setSequenceNumber(int sequenceNumber) {
            fieldSequenceNumber = sequenceNumber;
        }

    }

    private boolean fieldFetching = false;

    /**
     * The Configuration for this task
     */
    private ParsedConfiguration fieldConfiguration;

    /**
     * A List of ParsedDynamicAccountParameters, one for every <alllocal> entry
     * in the configuration.
     */
    private List<ParsedDynamicAccountParameters> fieldParsedDynamicAccountParameters;

    /**
     * The Static Accounts for this task. These are setup when the task is
     * configured.
     */
    private List<Account> fieldStaticAccounts;

    /**
     * The JavaMail Session for this fetch task.
     */

    private Session fieldSession;

    /**
     * The Dynamic Accounts for this task. These are setup each time the
     * fetchtask is run.
     */
    private Map<DynamicAccountKey, DynamicAccount> fieldDynamicAccounts;

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
     * Constructor for POP3mail.
     */
    public FetchMail() {
        super();
    }

    /**
     * Method configure parses and validates the Configuration data and creates
     * a new <code>ParsedConfiguration</code>, an <code>Account</code> for each
     * configured static account and a
     * <code>ParsedDynamicAccountParameters</code> for each dynamic account.
     */
    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        // Set any Session parameters passed in the Configuration
        setSessionParameters(configuration);

        // Create the ParsedConfiguration used in the delegation chain
        ParsedConfiguration parsedConfiguration = new ParsedConfiguration(configuration, getLocalUsers(), getDNSService(), getDomainList(), getMailQueue());

        setParsedConfiguration(parsedConfiguration);

        // Set up the Accounts
        List<HierarchicalConfiguration<ImmutableNode>> allAccounts = configuration.configurationsAt("accounts");
        if (allAccounts.size() < 1) {
            throw new ConfigurationException("Missing <accounts> section.");
        }
        if (allAccounts.size() > 1) {
            throw new ConfigurationException("Too many <accounts> sections, there must be exactly one");
        }
        HierarchicalConfiguration<ImmutableNode> accounts = allAccounts.get(0);

        if (!accounts.getKeys().hasNext()) {
            throw new ConfigurationException("Missing <account> section.");
        }

        int i = 0;
        // Create an Account for every configured account
        for (HierarchicalConfiguration<ImmutableNode> conf : configuration.childConfigurationsAt("accounts")) {

            String accountsChildName = conf.getRootElementName();

            if ("alllocal".equals(accountsChildName)) {
                // <allLocal> is dynamic, save the parameters for accounts to
                // be created when the task is triggered
                getParsedDynamicAccountParameters().add(new ParsedDynamicAccountParameters(i, conf));
                i++;
                continue;
            }

            if ("account".equals(accountsChildName)) {
                // Create an Account for the named user and
                // add it to the list of static accounts
                getStaticAccounts().add(new Account(i, parsedConfiguration, conf.getString("[@user]"), conf.getString("[@password]"), conf.getString("[@recipient]"), conf.getBoolean("[@ignorercpt-header]"), conf.getString("[@customrcpt-header]", ""), getSession()));
                i++;
                continue;
            }

            throw new ConfigurationException("Illegal token: <" + accountsChildName + "> in <accounts>");
        }
    }

    /**
     * Method target triggered fetches mail for each configured account.
     */
    @Override
    public void run() {
        // if we are already fetching then just return
        if (isFetching()) {
            LOGGER.info("Triggered fetch cancelled. A fetch is already in progress.");
            return;
        }

        // Enter Fetching State
        try {
            setFetching(true);
            LOGGER.info("Fetcher starting fetches");

            logJavaMailProperties();

            // Update the dynamic accounts,
            // merge with the static accounts and
            // sort the accounts so they are in the order
            // they were entered in config.xml
            updateDynamicAccounts();
            ArrayList<Account> mergedAccounts = new ArrayList<>(getDynamicAccounts().size() + getStaticAccounts().size());
            mergedAccounts.addAll(getDynamicAccounts().values());
            mergedAccounts.addAll(getStaticAccounts());
            Collections.sort(mergedAccounts);

            LOGGER.info("Processing {} static accounts and {} dynamic accounts.", getStaticAccounts().size(), getDynamicAccounts().size());

            // Fetch each account
            for (Account mergedAccount : mergedAccounts) {
                try {
                    new StoreProcessor(mergedAccount).process();
                } catch (MessagingException ex) {
                    LOGGER.error("A MessagingException has terminated processing of this Account", ex);
                }
            }
        } catch (Exception ex) {
            LOGGER.error("An Exception has terminated this fetch.", ex);
        } finally {
            LOGGER.info("Fetcher completed fetches");

            // Exit Fetching State
            setFetching(false);
        }
    }

    private void logJavaMailProperties() {
        // if debugging, list the JavaMail property key/value pairs
        // for this Session
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Session properties:");
            Properties properties = getSession().getProperties();
            for (Object o : properties.keySet()) {
                String key = (String) o;
                String val = (String) properties.get(key);
                if (val.length() > 40) {
                    val = val.substring(0, 37) + "...";
                }
                LOGGER.debug(key + "=" + val);

            }
        }
    }

    /**
     * Returns the fetching.
     *
     * @return boolean
     */
    protected boolean isFetching() {
        return fieldFetching;
    }

    /**
     * Sets the fetching.
     *
     * @param fetching The fetching to set
     */
    protected void setFetching(boolean fetching) {
        fieldFetching = fetching;
    }

    /**
     * Returns the configuration.
     *
     * @return ParsedConfiguration
     */
    protected ParsedConfiguration getConfiguration() {
        return fieldConfiguration;
    }

    /**
     * Sets the configuration.
     *
     * @param configuration The configuration to set
     */
    protected void setParsedConfiguration(ParsedConfiguration configuration) {
        fieldConfiguration = configuration;
    }

    /**
     * Returns the localUsers.
     *
     * @return UsersRepository
     */
    protected UsersRepository getLocalUsers() {
        return fieldLocalUsers;
    }

    /**
     * Returns the DNSService.
     *
     * @return DNSService
     */
    protected DNSService getDNSService() {
        return dnsServer;
    }

    public void setDNSService(DNSService dns) {
        this.dnsServer = dns;
    }

    public void setUsersRepository(UsersRepository urepos) {
        this.fieldLocalUsers = urepos;
    }

    /**
     * Returns the accounts. Initializes if required.
     *
     * @return List
     */
    protected List<Account> getStaticAccounts() {
        if (null == getStaticAccountsBasic()) {
            updateStaticAccounts();
            return getStaticAccounts();
        }
        return fieldStaticAccounts;
    }

    /**
     * Returns the staticAccounts.
     *
     * @return List
     */
    private List<Account> getStaticAccountsBasic() {
        return fieldStaticAccounts;
    }

    /**
     * Sets the accounts.
     *
     * @param accounts The accounts to set
     */
    protected void setStaticAccounts(List<Account> accounts) {
        fieldStaticAccounts = accounts;
    }

    /**
     * Updates the staticAccounts.
     */
    protected void updateStaticAccounts() {
        setStaticAccounts(computeStaticAccounts());
    }

    /**
     * Updates the ParsedDynamicAccountParameters.
     */
    protected void updateParsedDynamicAccountParameters() {
        setParsedDynamicAccountParameters(computeParsedDynamicAccountParameters());
    }

    /**
     * Updates the dynamicAccounts.
     */
    protected void updateDynamicAccounts() throws ConfigurationException {
        setDynamicAccounts(computeDynamicAccounts());
    }

    /**
     * Computes the staticAccounts.
     */
    protected List<Account> computeStaticAccounts() {
        return new ArrayList<>();
    }

    /**
     * Computes the ParsedDynamicAccountParameters.
     */
    protected List<ParsedDynamicAccountParameters> computeParsedDynamicAccountParameters() {
        return new ArrayList<>();
    }

    /**
     * Computes the dynamicAccounts.
     */
    protected Map<DynamicAccountKey, DynamicAccount> computeDynamicAccounts() throws ConfigurationException {
        Map<DynamicAccountKey, DynamicAccount> newAccounts;
        try {
            newAccounts = new HashMap<>(getLocalUsers().countUsers() * getParsedDynamicAccountParameters().size());
        } catch (UsersRepositoryException e) {
            throw new ConfigurationException("Unable to acces UsersRepository", e);
        }
        Map<DynamicAccountKey, DynamicAccount> oldAccounts = getDynamicAccountsBasic();
        if (null == oldAccounts) {
            oldAccounts = new HashMap<>(0);
        }

        // Process each ParsedDynamicParameters
        for (ParsedDynamicAccountParameters parsedDynamicAccountParameters : getParsedDynamicAccountParameters()) {
            Map<DynamicAccountKey, DynamicAccount> accounts = computeDynamicAccounts(oldAccounts, parsedDynamicAccountParameters);
            // Remove accounts from oldAccounts.
            // This avoids an average 2*N increase in heapspace used as the
            // newAccounts are created.
            Iterator<DynamicAccountKey> oldAccountsIterator = oldAccounts.keySet().iterator();
            while (oldAccountsIterator.hasNext()) {
                if (accounts.containsKey(oldAccountsIterator.next())) {
                    oldAccountsIterator.remove();
                }
            }
            // Add this parameter's accounts to newAccounts
            newAccounts.putAll(accounts);
        }
        return newAccounts;
    }

    /**
     * Returns the dynamicAccounts. Initializes if required.
     *
     * @return Map
     */
    protected Map<DynamicAccountKey, DynamicAccount> getDynamicAccounts() throws ConfigurationException {
        if (null == getDynamicAccountsBasic()) {
            updateDynamicAccounts();
            return getDynamicAccounts();
        }
        return fieldDynamicAccounts;
    }

    /**
     * Returns the dynamicAccounts.
     *
     * @return Map
     */
    private Map<DynamicAccountKey, DynamicAccount> getDynamicAccountsBasic() {
        return fieldDynamicAccounts;
    }

    /**
     * Sets the dynamicAccounts.
     *
     * @param dynamicAccounts The dynamicAccounts to set
     */
    protected void setDynamicAccounts(Map<DynamicAccountKey, DynamicAccount> dynamicAccounts) {
        fieldDynamicAccounts = dynamicAccounts;
    }

    /**
     * Compute the dynamicAccounts for the passed parameters. Accounts for
     * existing users are copied and accounts for new users are created.
     *
     * @param oldAccounts
     * @param parameters
     * @return Map - The current Accounts
     * @throws ConfigurationException
     */
    protected Map<DynamicAccountKey, DynamicAccount> computeDynamicAccounts(Map<DynamicAccountKey, DynamicAccount> oldAccounts, ParsedDynamicAccountParameters parameters) throws ConfigurationException {

        Map<DynamicAccountKey, DynamicAccount> accounts;
        Iterator<Username> usersIterator;
        try {
            accounts = new HashMap<>(getLocalUsers().countUsers());
            usersIterator = getLocalUsers().list();

        } catch (UsersRepositoryException e) {
            throw new ConfigurationException("Unable to access UsersRepository", e);
        }
        while (usersIterator.hasNext()) {
            String userName = usersIterator.next().asString();
            DynamicAccountKey key = new DynamicAccountKey(userName, parameters.getSequenceNumber());
            DynamicAccount account = oldAccounts.get(key);
            if (null == account) {
                // Create a new DynamicAccount
                account = new DynamicAccount(parameters.getSequenceNumber(), getConfiguration(), userName, parameters.getUserPrefix(), parameters.getUserSuffix(), parameters.getPassword(), parameters.getRecipientPrefix(), parameters.getRecipientSuffix(), parameters.isIgnoreRecipientHeader(),
                        parameters.getCustomRecipientHeader(), getSession());
            }
            accounts.put(key, account);
        }
        return accounts;
    }

    /**
     * Resets the dynamicAccounts.
     */
    protected void resetDynamicAccounts() {
        setDynamicAccounts(null);
    }

    /**
     * Returns the ParsedDynamicAccountParameters.
     *
     * @return List
     */
    protected List<ParsedDynamicAccountParameters> getParsedDynamicAccountParameters() {
        if (null == getParsedDynamicAccountParametersBasic()) {
            updateParsedDynamicAccountParameters();
            return getParsedDynamicAccountParameters();
        }
        return fieldParsedDynamicAccountParameters;
    }

    /**
     * Returns the ParsedDynamicAccountParameters.
     *
     * @return List
     */
    private List<ParsedDynamicAccountParameters> getParsedDynamicAccountParametersBasic() {
        return fieldParsedDynamicAccountParameters;
    }

    /**
     * Sets the ParsedDynamicAccountParameters.
     *
     * @param parsedDynamicAccountParameters The ParsedDynamicAccountParameters to set
     */
    protected void setParsedDynamicAccountParameters(List<ParsedDynamicAccountParameters> parsedDynamicAccountParameters) {
        fieldParsedDynamicAccountParameters = parsedDynamicAccountParameters;
    }

    /**
     * Returns the session, lazily initialized if required.
     *
     * @return Session
     */
    protected Session getSession() {
        Session session;
        if (null == (session = getSessionBasic())) {
            updateSession();
            return getSession();
        }
        return session;
    }

    /**
     * Returns the session.
     *
     * @return Session
     */
    private Session getSessionBasic() {
        return fieldSession;
    }

    /**
     * Answers a new Session.
     *
     * @return Session
     */
    protected Session computeSession() {
        // Make separate properties instance so the
        // fetchmail.xml <javaMailProperties> can override the
        // property values without interfering with other fetchmail instances
        return Session.getInstance(new Properties(System.getProperties()));
    }

    /**
     * Updates the current Session.
     */
    protected void updateSession() {
        setSession(computeSession());
    }

    /**
     * Sets the session.
     *
     * @param session The session to set
     */
    protected void setSession(Session session) {
        fieldSession = session;
    }

    /**
     * Propagate any Session parameters in the configuration to the Session.
     *
     * @param configuration The configuration containing the parameters
     * @throws ConfigurationException
     */
    protected void setSessionParameters(HierarchicalConfiguration<ImmutableNode> configuration) {

        if (configuration.getKeys("javaMailProperties.property").hasNext()) {
            Properties properties = getSession().getProperties();
            List<HierarchicalConfiguration<ImmutableNode>> allProperties = configuration.configurationsAt("javaMailProperties.property");
            for (HierarchicalConfiguration<ImmutableNode> propConf : allProperties) {
                String nameProp = propConf.getString("[@name]");
                String valueProp = propConf.getString("[@value]");
                properties.setProperty(nameProp, valueProp);
                LOGGER.debug("Set property name: {} to: {}", nameProp, valueProp);
            }
        }
    }

    public void setMailQueue(MailQueue queue) {
        this.queue = queue;
    }

    public MailQueue getMailQueue() {
        return queue;
    }

    public void setDomainList(DomainList domainList) {
        this.domainList = domainList;
    }

    public DomainList getDomainList() {
        return domainList;
    }
}
