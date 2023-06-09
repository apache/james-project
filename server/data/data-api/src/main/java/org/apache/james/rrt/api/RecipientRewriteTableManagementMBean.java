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

import org.apache.james.rrt.lib.Mappings;

/**
 * Expose virtualusertable management functionality through JMX.
 */
public interface RecipientRewriteTableManagementMBean {

    /**
     * Add regex mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param regex
     *            the regex.
     * @throws Exception
     *            If an error occurred
     */
    void addRegexMapping(String user, String domain, String regex) throws Exception;

    /**
     * Remove regex mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param regex
     *            the regex.
     * @throws Exception
     *            If an error occurred
     */
    void removeRegexMapping(String user, String domain, String regex) throws Exception;

    /***
     * Add address mapping that, for a user from@fromDomain would redirect
     * mails to toAddress
     *
     * Prefer using the specific methods addUserAliasMapping, addDomainMapping...
     * which create an alias with a more specific Mapping.Type
     * 
     * @param fromUser
     *            the username. Null if no username should be used
     * @param fromDomain
     *            the domain. Null if no domain should be used
     * @param toAddress
     *            the address.
     *
     */
    void addAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception;

    /**
     * Remove address mapping. The API takes the same arguments as addAddressMapping
     *
     * @param fromUser
     *            the username. Null if no username should be used
     * @param fromDomain
     *            the domain. Null if no domain should be used
     * @param toAddress
     *
     */
    void removeAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception;

    /**
     * Add error mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param error
     * @throws Exception
     *            If an error occurred
     */
    void addErrorMapping(String user, String domain, String error) throws Exception;

    /**
     * Remove error mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param error
     * @throws Exception
     *            If an error occurred
     */
    void removeErrorMapping(String user, String domain, String error) throws Exception;

    /**
     * Add domain mapping
     * 
     * @param domain
     *            the domain. Null if no domain should be used
     * @param targetDomain
     *            the target domain for the mapping
     * @throws Exception
     *            If an error occurred
     */
    void addDomainMapping(String domain, String targetDomain) throws Exception;

    /**
     * Remove domain mapping
     * 
     * @param domain
     *            the domain. Null if no domain should be used
     * @param targetDomain
     *            the target domain for the mapping
     * 
     * @throws Exception
     *            If an error occurred
     */
    void removeDomainMapping(String domain, String targetDomain) throws Exception;

    /**
     * Return a Map which holds domain redirections
     *
     * @param domain
     *            the domain. Null if no domain should be used
     *
     * @throws Exception
     *            If an error occurred
     */
    Mappings getDomainMappings(String domain) throws Exception;

    /**
     * Return the explicit mapping stored for the given user and domain. Return
     * null if no mapping was found
     * 
     * @param user
     *            the username
     * @param domain
     *            the domain
     * @return the collection which holds the mappings.
     * @throws Exception
     *            If an error occurred
     */
    Mappings getUserDomainMappings(String user, String domain) throws Exception;

    /**
     * Try to identify the right method based on the prefix of the mapping and
     * add it.
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param mapping
     *            the mapping.
     * @throws Exception
     *            If an error occurred
     */
    void addMapping(String user, String domain, String mapping) throws Exception;

    /**
     * Try to identify the right method based on the prefix of the mapping and
     * remove it.
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param mapping
     *            the mapping.
     * @throws Exception
     *            If an error occurred
     */
    void removeMapping(String user, String domain, String mapping) throws Exception;

    /**
     * Return a Map which holds all mappings. The key is the user@domain and the
     * value is a Collection which holds all mappings
     * 
     * @return Map which holds all mappings
     * @throws Exception
     *            If an error occurred
     */
    Map<String, Mappings> getAllMappings() throws Exception;
}
