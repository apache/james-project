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

package org.apache.james.mailrepository.jpa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.apache.james.mailrepository.api.MailRepositoryUrl;

@Entity(name = "JamesMailRepos")
@Table(name = "JAMES_MAIL_REPOS")
@NamedQueries({
    @NamedQuery(name = "listUrls", query = "SELECT url FROM JamesMailRepos url"),
    @NamedQuery(name = "getUrl", query = "SELECT url FROM JamesMailRepos url WHERE url.value=:value")})
public class JPAUrl {
    public static JPAUrl from(MailRepositoryUrl url) {
        return new JPAUrl(url.asString());
    }

    @Id
    @Column(name = "MAIL_REPO_NAME", nullable = false)
    private String value;

    /**
     * Default no-args constructor for JPA class enhancement.
     * The constructor need to be public or protected to be used by JPA.
     * See:  http://docs.oracle.com/javaee/6/tutorial/doc/bnbqa.html
     * Do not us this constructor, it is for JPA only.
     */
    protected JPAUrl() {
    }

    public JPAUrl(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public MailRepositoryUrl toMailRepositoryUrl() {
        return MailRepositoryUrl.from(value);
    }
}
