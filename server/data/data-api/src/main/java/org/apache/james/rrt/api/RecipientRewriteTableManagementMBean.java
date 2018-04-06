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
     * Add address mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param address
     *            the address.
     * @throws Exception
     *            If an error occurred
     */
    void addAddressMapping(String user, String domain, String address) throws Exception;

    /**
     * Remove address mapping
     * 
     * @param user
     *            the username. Null if no username should be used
     * @param domain
     *            the domain. Null if no domain should be used
     * @param address
     * @throws Exception
     *            If an error occurred
     */
    void removeAddressMapping(String user, String domain, String address) throws Exception;

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

    /***
     * Add forward mapping
     *
     * @param toUser
     *            the username part of the mail address destination defined for this forward.
     * @param toDomain
     *            the domain part of the mail address destination defined for this forward.
     * @param fromAddress The base address of the forward. Mails for this address will be sent to the added forward destination.
     * @throws Exception If an error occurred
     */
    void addForwardMapping(String user, String domain, String address) throws Exception;

    /**
     * Remove forward mapping
     * 
     * @param toUser
     *            the username part of the mail address destination defined for this forward.
     * @param toDomain
     *            the domain part of the mail address destination defined for this forward.
     * @param fromAddress The base address of the forward. Mails for this address will no more sent to the removed forward destination.
     * @throws Exception If an error occurred
     */
    void removeForwardMapping(String toUser, String toDomain, String fromAddress) throws Exception;

    /***
     * Add group mapping
     *
     * @param toUser
     *            the username part of the mail address destination defined for this group.
     * @param toDomain
     *            the domain part of the mail address destination defined for this group.
     * @param fromAddress The base address of the group. Mails for this address will be sent to the added group destination.
     * @throws Exception If an error occurred
     */
    void addGroupMapping(String user, String domain, String address) throws Exception;

    /**
     * Remove group mapping
     *
     * @param toUser
     *            the username part of the mail address destination defined for this group.
     * @param toDomain
     *            the domain part of the mail address destination defined for this group.
     * @param fromAddress The base address of the forward. Mails for this address will no more sent to the removed group destination.
     * @throws Exception If an error occurred
     */
    void removeGroupMapping(String toUser, String toDomain, String fromAddress) throws Exception;
}
