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
package org.apache.james.rrt.lib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.internet.ParseException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.LogEnabled;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;

import com.google.common.annotations.VisibleForTesting;

/**
 * 
 */
public abstract class AbstractRecipientRewriteTable implements RecipientRewriteTable, LogEnabled, Configurable {
    // The maximum mappings which will process before throwing exception
    private int mappingLimit = 10;

    private boolean recursive = true;

    private Logger logger;

    private DomainList domainList;

    @Inject
    @Resource
    public void setDomainList(@Named("domainlist") DomainList domainList) {
        this.domainList = domainList;
    }

    /**
     * @see org.apache.james.lifecycle.api.Configurable#configure(HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        setRecursiveMapping(config.getBoolean("recursiveMapping", true));
        try {
            setMappingLimit(config.getInt("mappingLimit", 10));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(e.getMessage());
        }
        doConfigure(config);
    }

    public void setLog(Logger logger) {
        this.logger = logger;
    }

    /**
     * Override to handle config
     * 
     * @param conf
     * @throws ConfigurationException
     */
    protected void doConfigure(HierarchicalConfiguration conf) throws ConfigurationException {

    }

    public void setRecursiveMapping(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * Set the mappingLimit
     * 
     * @param mappingLimit
     *            the mappingLimit
     * @throws IllegalArgumentException
     *             get thrown if mappingLimit smaller then 1 is used
     */
    public void setMappingLimit(int mappingLimit) throws IllegalArgumentException {
        if (mappingLimit < 1)
            throw new IllegalArgumentException("The minimum mappingLimit is 1");
        this.mappingLimit = mappingLimit;
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#getMappings(String,
     *      String)
     */
    public Collection<String> getMappings(String user, String domain) throws ErrorMappingException, RecipientRewriteTableException {
        return getMappings(user, domain, mappingLimit);
    }

    public Collection<String> getMappings(String user, String domain, int mappingLimit) throws ErrorMappingException, RecipientRewriteTableException {

        // We have to much mappings throw ErrorMappingException to avoid
        // infinity loop
        if (mappingLimit == 0)
            throw new ErrorMappingException("554 Too many mappings to process");

        String targetString = mapAddress(user, domain);

        // Only non-null mappings are translated
        if (targetString != null) {
            Collection<String> mappings = new ArrayList<String>();
            if (targetString.startsWith(RecipientRewriteTable.ERROR_PREFIX)) {
                throw new ErrorMappingException(targetString.substring(RecipientRewriteTable.ERROR_PREFIX.length()));

            } else {

                for (String target : RecipientRewriteTableUtil.mappingToCollection(targetString)) {
                    if (target.startsWith(RecipientRewriteTable.REGEX_PREFIX)) {
                        try {
                            target = RecipientRewriteTableUtil.regexMap(new MailAddress(user, domain), target);
                        } catch (PatternSyntaxException e) {
                            getLogger().error("Exception during regexMap processing: ", e);
                        } catch (ParseException e) {
                            // should never happen
                            getLogger().error("Exception during regexMap processing: ", e);
                        }
                    } else if (target.startsWith(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) {
                        target = user + "@" + target.substring(RecipientRewriteTable.ALIASDOMAIN_PREFIX.length());
                    }

                    if (target == null)
                        continue;

                    String buf = "Valid virtual user mapping " + user + "@" + domain + " to " + target;
                    getLogger().debug(buf);

                    if (recursive) {

                        String userName;
                        String domainName;
                        String args[] = target.split("@");

                        if (args != null && args.length > 1) {

                            userName = args[0];
                            domainName = args[1];
                        } else {
                            // TODO Is that the right todo here?
                            userName = target;
                            domainName = domain;
                        }

                        // Check if the returned mapping is the same as the
                        // input. If so return null to avoid loops
                        if (userName.equalsIgnoreCase(user) && domainName.equalsIgnoreCase(domain)) {
                            return null;
                        }

                        Collection<String> childMappings = getMappings(userName, domainName, mappingLimit - 1);

                        if (childMappings == null) {
                            // add mapping
                            mappings.add(target);
                        } else {
                            mappings.addAll(childMappings);
                        }

                    } else {
                        mappings.add(target);
                    }
                }
            }

            return mappings;

        }

        return null;
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#addRegexMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void addRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new RecipientRewriteTableException("Invalid regex: " + regex, e);
        }

        checkMapping(user, domain, regex);
        getLogger().info("Add regex mapping => " + regex + " for user: " + user + " domain: " + domain);
        addMappingInternal(user, domain, RecipientRewriteTable.REGEX_PREFIX + regex);

    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#removeRegexMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void removeRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        getLogger().info("Remove regex mapping => " + regex + " for user: " + user + " domain: " + domain);
        removeMappingInternal(user, domain, RecipientRewriteTable.REGEX_PREFIX + regex);
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#addAddressMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void addAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        if (address.indexOf('@') < 0) {
            try {
                address = address + "@" + domainList.getDefaultDomain();
            } catch (DomainListException e) {
                throw new RecipientRewriteTableException("Unable to retrieve default domain", e);
            }
        }
        try {
            new MailAddress(address);
        } catch (ParseException e) {
            throw new RecipientRewriteTableException("Invalid emailAddress: " + address, e);
        }
        checkMapping(user, domain, address);
        getLogger().info("Add address mapping => " + address + " for user: " + user + " domain: " + domain);
        addMappingInternal(user, domain, address);

    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#removeAddressMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void removeAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        if (address.indexOf('@') < 0) {
            try {
                address = address + "@" + domainList.getDefaultDomain();
            } catch (DomainListException e) {
                throw new RecipientRewriteTableException("Unable to retrieve default domain", e);
            }
        }
        getLogger().info("Remove address mapping => " + address + " for user: " + user + " domain: " + domain);
        removeMappingInternal(user, domain, address);
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#addErrorMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void addErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        checkMapping(user, domain, error);
        getLogger().info("Add error mapping => " + error + " for user: " + user + " domain: " + domain);
        addMappingInternal(user, domain, RecipientRewriteTable.ERROR_PREFIX + error);

    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#removeErrorMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void removeErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        getLogger().info("Remove error mapping => " + error + " for user: " + user + " domain: " + domain);
        removeMappingInternal(user, domain, RecipientRewriteTable.ERROR_PREFIX + error);
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#addMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void addMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {

        String map = mapping.toLowerCase();

        if (map.startsWith(RecipientRewriteTable.ERROR_PREFIX)) {
            addErrorMapping(user, domain, map.substring(RecipientRewriteTable.ERROR_PREFIX.length()));
        } else if (map.startsWith(RecipientRewriteTable.REGEX_PREFIX)) {
            addRegexMapping(user, domain, map.substring(RecipientRewriteTable.REGEX_PREFIX.length()));
        } else if (map.startsWith(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) {
            if (user != null)
                throw new RecipientRewriteTableException("User must be null for aliasDomain mappings");
            addAliasDomainMapping(domain, map.substring(RecipientRewriteTable.ALIASDOMAIN_PREFIX.length()));
        } else {
            addAddressMapping(user, domain, map);
        }

    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#removeMapping(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    public void removeMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {

        String map = mapping.toLowerCase();

        if (map.startsWith(RecipientRewriteTable.ERROR_PREFIX)) {
            removeErrorMapping(user, domain, map.substring(RecipientRewriteTable.ERROR_PREFIX.length()));
        } else if (map.startsWith(RecipientRewriteTable.REGEX_PREFIX)) {
            removeRegexMapping(user, domain, map.substring(RecipientRewriteTable.REGEX_PREFIX.length()));
        } else if (map.startsWith(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) {
            if (user != null)
                throw new RecipientRewriteTableException("User must be null for aliasDomain mappings");
            removeAliasDomainMapping(domain, map.substring(RecipientRewriteTable.ALIASDOMAIN_PREFIX.length()));
        } else {
            removeAddressMapping(user, domain, map);
        }

    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#getAllMappings()
     */
    public Map<String, Collection<String>> getAllMappings() throws RecipientRewriteTableException {
        int count = 0;
        Map<String, Collection<String>> mappings = getAllMappingsInternal();

        if (mappings != null) {
            count = mappings.size();
        }
        getLogger().debug("Retrieve all mappings. Mapping count: " + count);
        return mappings;
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#getUserDomainMappings(java.lang.String,
     *      java.lang.String)
     */
    public Collection<String> getUserDomainMappings(String user, String domain) throws RecipientRewriteTableException {
        return getUserDomainMappingsInternal(user, domain);
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#addAliasDomainMapping(java.lang.String,
     *      java.lang.String)
     */
    public void addAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        getLogger().info("Add domain mapping: " + aliasDomain + " => " + realDomain);
        addMappingInternal(null, aliasDomain, RecipientRewriteTable.ALIASDOMAIN_PREFIX + realDomain);
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTable#removeAliasDomainMapping(java.lang.String,
     *      java.lang.String)
     */
    public void removeAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        getLogger().info("Remove domain mapping: " + aliasDomain + " => " + realDomain);
        removeMappingInternal(null, aliasDomain, RecipientRewriteTable.ALIASDOMAIN_PREFIX + realDomain);
    }

    protected Logger getLogger() {
        return logger;
    }

    /**
     * Add new mapping
     * 
     * @param user
     *            the user
     * @param domain
     *            the domain
     * @param mapping
     *            the mapping
     * @throws InvalidMappingException
     */
    protected abstract void addMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException;

    /**
     * Remove mapping
     * 
     * @param user
     *            the user
     * @param domain
     *            the domain
     * @param mapping
     *            the mapping
     * @throws InvalidMappingException
     */
    protected abstract void removeMappingInternal(String user, String domain, String mapping) throws RecipientRewriteTableException;

    /**
     * Return Collection of all mappings for the given username and domain
     * 
     * @param user
     *            the user
     * @param domain
     *            the domain
     * @return Collection which hold the mappings
     */
    protected abstract Collection<String> getUserDomainMappingsInternal(String user, String domain) throws RecipientRewriteTableException;

    /**
     * Return a Map which holds all Mappings
     * 
     * @return Map
     */
    protected abstract Map<String, Collection<String>> getAllMappingsInternal() throws RecipientRewriteTableException;

    /**
     * Override to map virtual recipients to real recipients, both local and
     * non-local. Each key in the provided map corresponds to a potential
     * virtual recipient, stored as a <code>MailAddress</code> object.
     * 
     * Translate virtual recipients to real recipients by mapping a string
     * containing the address of the real recipient as a value to a key. Leave
     * the value <code>null<code>
     * if no mapping should be performed. Multiple recipients may be specified by delineating
     * the mapped string with commas, semi-colons or colons.
     * 
     * @param user
     *            the mapping of virtual to real recipients, as
     *            <code>MailAddress</code>es to <code>String</code>s.
     */
    protected abstract String mapAddressInternal(String user, String domain) throws RecipientRewriteTableException;

    /**
     * Get all mappings for the given user and domain. If a aliasdomain mapping
     * was found get sure it is in the map as first mapping.
     * 
     * @param user
     *            the username
     * @param domain
     *            the domain
     * @return the mappings
     */
    private String mapAddress(String user, String domain) throws RecipientRewriteTableException {

        String mappings = mapAddressInternal(user, domain);

        return sortMappings(mappings);
    }

    @VisibleForTesting static String sortMappings(String mappings) {
        // check if we need to sort
        // TODO: Maybe we should just return the aliasdomain mapping
        if (mappings != null && mappings.contains(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) {
            Collection<String> mapCol = RecipientRewriteTableUtil.mappingToCollection(mappings);
            Iterator<String> mapIt = mapCol.iterator();

            List<String> col = new ArrayList<String>(mapCol.size());

            int i = 0;
            while (mapIt.hasNext()) {
                String mapping = mapIt.next();

                if (mapping.startsWith(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) {
                    col.add(i, mapping);
                    i++;
                } else {
                    col.add(mapping);
                }
            }
            return RecipientRewriteTableUtil.CollectionToMapping(col);
        } else {
            return mappings;
        }
    }

    private void checkMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        Collection<String> mappings = getUserDomainMappings(user, domain);
        if (mappings != null && mappings.contains(mapping)) {
            throw new RecipientRewriteTableException("Mapping " + mapping + " for user " + user + " domain " + domain + " already exist!");
        }
    }

    /**
     * Return user String for the given argument.
     * If give value is null, return a wildcard.
     * 
     * @param user the given user String
     * @return fixedUser the fixed user String
     * @throws InvalidMappingException get thrown on invalid argument
     */
    protected String getFixedUser(String user) {
        if (user != null) {
            if (user.equals(WILDCARD) || !user.contains("@")) {
                return user;
            } else {
                throw new IllegalArgumentException("Invalid user: " + user);
            }
        } else {
            return WILDCARD;
        }
    }

    /**
     * Fix the domain for the given argument.
     * If give value is null, return a wildcard.
     * 
     * @param domain the given domain String
     * @return fixedDomain the fixed domain String
     * @throws InvalidMappingException get thrown on invalid argument
     */
    protected String getFixedDomain(String domain) {
        if (domain != null) {
            if (domain.equals(WILDCARD) || !domain.contains("@")) {
                return domain;
            } else {
                throw new IllegalArgumentException("Invalid domain: " + domain);
            }
        } else {
            return WILDCARD;
        }
    }

}
