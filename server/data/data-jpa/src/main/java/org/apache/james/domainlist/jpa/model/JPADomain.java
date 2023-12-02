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
package org.apache.james.domainlist.jpa.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.apache.james.core.Domain;

/**
 * Domain class for the James Domain to be used for JPA persistence.
 */
@Entity(name = "JamesDomain")
@Table(name = "JAMES_DOMAIN")
@NamedQueries({ 
    @NamedQuery(name = "findDomainByName", query = "SELECT domain FROM JamesDomain domain WHERE domain.name=:name"), 
    @NamedQuery(name = "containsDomain", query = "SELECT COUNT(domain) FROM JamesDomain domain WHERE domain.name=:name"),
    @NamedQuery(name = "listDomainNames", query = "SELECT domain.name FROM JamesDomain domain"), 
    @NamedQuery(name = "deleteDomainByName", query = "DELETE FROM JamesDomain domain WHERE domain.name=:name") })
public class JPADomain {

    /**
     * The name of the domain. column name is chosen to be compatible with the
     * JDBCDomainList.
     */
    @Id
    @Column(name = "DOMAIN_NAME", nullable = false, length = 100)
    private String name;

    /**
     * Default no-args constructor for JPA class enhancement.
     * The constructor need to be public or protected to be used by JPA.
     * See:  http://docs.oracle.com/javaee/6/tutorial/doc/bnbqa.html
     * Do not us this constructor, it is for JPA only.
     */
    protected JPADomain() {
    }

    /**
     * Use this simple constructor to create a new Domain.
     * 
     * @param name
     *            the name of the Domain
     */
    public JPADomain(Domain name) {
        this.name = name.asString();
    }

}
