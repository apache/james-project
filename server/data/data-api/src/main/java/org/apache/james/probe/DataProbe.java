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

package org.apache.james.probe;

import java.util.List;
import java.util.Map;

import org.apache.james.rrt.lib.Mappings;

public interface DataProbe {

    void addUser(String userName, String password) throws Exception;

    void removeUser(String username) throws Exception;

    void setPassword(String userName, String password) throws Exception;

    String[] listUsers() throws Exception;

    void addDomain(String domain) throws Exception;

    boolean containsDomain(String domain) throws Exception;

    String getDefaultDomain() throws Exception;

    void removeDomain(String domain) throws Exception;

    List<String> listDomains() throws Exception;

    Map<String, Mappings> listMappings() throws Exception;

    Mappings listUserDomainMappings(String user, String domain) throws Exception;

    void addAddressMapping(String user, String domain, String toAddress) throws Exception;

    void removeAddressMapping(String user, String domain, String fromAddress) throws Exception;

    void addRegexMapping(String user, String domain, String regex) throws Exception;

    void removeRegexMapping(String user, String domain, String regex) throws Exception;

    void addDomainAliasMapping(String aliasDomain, String deliveryDomain) throws Exception;

    void addForwardMapping(String toUser, String toDomain, String fromAddress) throws Exception;

    void removeForwardMapping(String toUser, String toDomain, String fromAddress) throws Exception;

    void addGroupMapping(String toUser, String toDomain, String fromAddress) throws Exception;

    void removeGroupMapping(String toUser, String toDomain, String fromAddress) throws Exception;
}