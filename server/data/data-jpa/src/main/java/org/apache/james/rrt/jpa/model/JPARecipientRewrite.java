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
package org.apache.james.rrt.jpa.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.apache.james.core.Domain;

import com.google.common.base.Objects;

/**
 * RecipientRewriteTable class for the James Virtual User Table to be used for JPA
 * persistence.
 */
@Entity(name = "JamesRecipientRewrite")
@Table(name = JPARecipientRewrite.JAMES_RECIPIENT_REWRITE)
@NamedQueries({ 
    @NamedQuery(name = "selectUserDomainMapping", query = "SELECT rrt FROM JamesRecipientRewrite rrt WHERE rrt.user=:user AND rrt.domain=:domain"),
    @NamedQuery(name = "selectAllMappings", query = "SELECT rrt FROM JamesRecipientRewrite rrt"),
    @NamedQuery(name = "deleteMapping", query = "DELETE FROM JamesRecipientRewrite rrt WHERE rrt.user=:user AND rrt.domain=:domain AND rrt.targetAddress=:targetAddress"),
    @NamedQuery(name = "updateMapping", query = "UPDATE JamesRecipientRewrite rrt SET rrt.targetAddress=:targetAddress WHERE rrt.user=:user AND rrt.domain=:domain"),
    @NamedQuery(name = "selectSourcesByMapping", query = "SELECT rrt FROM JamesRecipientRewrite rrt WHERE rrt.targetAddress=:targetAddress") })
@IdClass(JPARecipientRewrite.RecipientRewriteTableId.class)
public class JPARecipientRewrite {

    public static final String JAMES_RECIPIENT_REWRITE = "JAMES_RECIPIENT_REWRITE";

    public static class RecipientRewriteTableId implements Serializable {

        private static final long serialVersionUID = 1L;

        private String user;

        private String domain;

        public RecipientRewriteTableId() {
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(user, domain);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final RecipientRewriteTableId other = (RecipientRewriteTableId) obj;
            return Objects.equal(this.user, other.user) && Objects.equal(this.domain, other.domain);
        }
    }

    /**
     * The name of the user.
     */
    @Id
    @Column(name = "USER_NAME", nullable = false, length = 100)
    private String user = "";

    /**
     * The name of the domain. Column name is chosen to be compatible with the
     * JDBCRecipientRewriteTableList.
     */
    @Id
    @Column(name = "DOMAIN_NAME", nullable = false, length = 100)
    private String domain = "";

    /**
     * The target address. column name is chosen to be compatible with the
     * JDBCRecipientRewriteTableList.
     */
    @Column(name = "TARGET_ADDRESS", nullable = false, length = 100)
    private String targetAddress = "";
    
    /**
     * Default no-args constructor for JPA class enhancement.
     * The constructor need to be public or protected to be used by JPA.
     * See:  http://docs.oracle.com/javaee/6/tutorial/doc/bnbqa.html
     * Do not us this constructor, it is for JPA only.
     */
    protected JPARecipientRewrite() {
    }

    /**
     * Use this simple constructor to create a new RecipientRewriteTable.
     * 
     * @param user
     *            , domain and their associated targetAddress
     */
    public JPARecipientRewrite(String user, Domain domain, String targetAddress) {
        this.user = user;
        this.domain = domain.asString();
        this.targetAddress = targetAddress;
    }

    public String getUser() {
        return user;
    }

    public String getDomain() {
        return domain;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

}
