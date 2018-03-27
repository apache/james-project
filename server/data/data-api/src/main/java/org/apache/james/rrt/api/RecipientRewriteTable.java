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
package org.apache.james.rrt.api;

import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.Mappings;

/**
 * Interface which should be implemented of classes which map recipients.
 */
public interface RecipientRewriteTable {

    interface Domains {

        Domain WILDCARD = new Domain(RecipientRewriteTable.WILDCARD) {

            @Override
            public String name() {
                throw new IllegalStateException();
            }

            @Override
            public String toString() {
                return "Domain : * (Wildcard)";
            }
        };
    }

    /**
     * The wildcard used for alias domain mappings
     */
    String WILDCARD = "*";

    /**
     * Return the mapped MailAddress for the given address. Return null if no
     * matched mapping was found
     * 
     * @param user
     *            the MailAddress
     * @return the mapped mailAddress
     * @throws ErrorMappingException
     *             get thrown if an error mapping was found
     * @throws RecipientRewriteTableException
     */
    Mappings getMappings(String user, Domain domain) throws ErrorMappingException, RecipientRewriteTableException;

    /**
     * Add regex mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param regex
     *            the regex.
     * @throws RecipientRewriteTableException
     */
    void addRegexMapping(String user, Domain domain, String regex) throws RecipientRewriteTableException;

    /**
     * Remove regex mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param regex
     *            the regex.
     * @throws RecipientRewriteTableException
     */
    void removeRegexMapping(String user, Domain domain, String regex) throws RecipientRewriteTableException;

    /***
     * Add address mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param address
     * @throws RecipientRewriteTableException
     */
    void addAddressMapping(String user, Domain domain, String address) throws RecipientRewriteTableException;

    /**
     * Remove address mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param address
     * @throws RecipientRewriteTableException
     */
    void removeAddressMapping(String user, Domain domain, String address) throws RecipientRewriteTableException;

    /**
     * Add error mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param error
     *            the regex.
     * @throws RecipientRewriteTableException
     */
    void addErrorMapping(String user, Domain domain, String error) throws RecipientRewriteTableException;

    /**
     * Remove error mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param error
     * @throws RecipientRewriteTableException
     */
    void removeErrorMapping(String user, Domain domain, String error) throws RecipientRewriteTableException;

    /**
     * Return the explicit mapping stored for the given user and domain. Return
     * null if no mapping was found
     * 
     * @param user
     *            the username
     * @param domain
     *            the domain
     * @return the collection which holds the mappings.
     * @throws RecipientRewriteTableException
     */
    Mappings getUserDomainMappings(String user, Domain domain) throws RecipientRewriteTableException;

    /**
     * Add mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param mapping
     *            the mapping
     * @throws RecipientRewriteTableException
     */
    void addMapping(String user, Domain domain, String mapping) throws RecipientRewriteTableException;

    /**
     * Remove mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param mapping
     *            the mapping
     * @throws RecipientRewriteTableException
     */
    void removeMapping(String user, Domain domain, String mapping) throws RecipientRewriteTableException;

    /**
     * Return a Map which holds all mappings. The key is the user@domain and the
     * value is a Collection which holds all mappings
     * 
     * @return Map which holds all mappings
     * @throws RecipientRewriteTableException
     */
    Map<String, Mappings> getAllMappings() throws RecipientRewriteTableException;

    /**
     * Add aliasDomain mapping
     * 
     * @param aliasDomain
     *            the aliasdomain which should be mapped to the realDomain
     * @param realDomain
     *            the realDomain
     * @throws RecipientRewriteTableException
     */
    void addAliasDomainMapping(Domain aliasDomain, Domain realDomain) throws RecipientRewriteTableException;

    /**
     * Remove aliasDomain mapping
     * 
     * @param aliasDomain
     *            the aliasdomain which should be mapped to the realDomain
     * @param realDomain
     *            the realDomain
     * @throws RecipientRewriteTableException
     */
    void removeAliasDomainMapping(Domain aliasDomain, Domain realDomain) throws RecipientRewriteTableException;

    void addForwardMapping(String user, Domain domain, String address) throws RecipientRewriteTableException;

    void removeForwardMapping(String user, Domain domain, String address) throws RecipientRewriteTableException;

    class ErrorMappingException extends Exception {

        private static final long serialVersionUID = 2348752938798L;

        public ErrorMappingException(String string) {
            super(string);
        }

    }
}
