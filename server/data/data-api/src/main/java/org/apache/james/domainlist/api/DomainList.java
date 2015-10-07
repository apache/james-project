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
package org.apache.james.domainlist.api;

/**
 * This interface should be implemented by services which offer domains for
 * which email will accepted.
 */
public interface DomainList {

    /**
     * Return array of domains which should be used as localdomains. Return null
     * if no domain is found.
     * 
     * @return domains
     */
    String[] getDomains() throws DomainListException;

    /**
     * Return true if the domain exists in the service
     * 
     * @param domain
     *            the domain
     * @return true if the given domain exists in the service
     */
    boolean containsDomain(String domain) throws DomainListException;

    /**
     * Add domain to the service
     * 
     * @param domain
     *            domain to add
     * @throws DomainListException
     *            If the domain could not be added
     */
    void addDomain(String domain) throws DomainListException;

    /**
     * Remove domain from the service
     * 
     * @param domain
     *            domain to remove
     * @throws DomainListException
     *            If the domain could not be removed
     */
    void removeDomain(String domain) throws DomainListException;

    /**
     * Return the default domain which will get used to deliver mail to if only
     * the localpart was given on rcpt to.
     * 
     * @return the defaultdomain
     */
    String getDefaultDomain() throws DomainListException;

}
