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

package org.apache.james.droplists.jpa.model;

import static org.apache.james.droplists.api.OwnerScope.DOMAIN;

import java.util.Objects;

import jakarta.mail.internet.AddressException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;

import com.google.common.base.MoreObjects;

/**
 * A JPA entity representing a drop list entry.
 */
@Entity(name = "JamesDropList")
@Table(name = "JAMES_DROP_LIST")
@NamedQuery(name = "listDropListEntries",
    query = "SELECT j FROM JamesDropList j WHERE j.ownerScope = :ownerScope AND j.owner = :owner")
@NamedQuery(name = "queryDropListEntry",
    query = "SELECT j FROM JamesDropList j WHERE j.ownerScope = :ownerScope AND j.owner = :owner AND j.deniedEntity IN :deniedEntity")
@NamedQuery(name = "removeDropListEntry",
    query = "DELETE FROM JamesDropList j WHERE j.ownerScope = :ownerScope AND j.owner = :owner AND j.deniedEntity = :deniedEntity")
public class JPADropListEntry {

    @Id
    @GeneratedValue
    @Column(name = "DROPLIST_ID")
    private long id;

    @Column(name = "OWNER_SCOPE", nullable = false)
    private String ownerScope;

    @Column(name = "OWNER")
    private String owner;

    @Column(name = "DENIED_ENTITY_TYPE", nullable = false)
    private String deniedEntityType;

    @Column(name = "DENIED_ENTITY", nullable = false)
    private String deniedEntity;

    public JPADropListEntry() {
        //  Default no-args constructor for JPA class enhancement.
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getOwnerScope() {
        return ownerScope;
    }

    public void setOwnerScope(String ownerScope) {
        this.ownerScope = ownerScope;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDeniedEntityType() {
        return deniedEntityType;
    }

    public void setDeniedEntityType(String deniedEntityType) {
        this.deniedEntityType = deniedEntityType;
    }

    public String getDeniedEntity() {
        return deniedEntity;
    }

    public void setDeniedEntity(String deniedEntity) {
        this.deniedEntity = deniedEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JPADropListEntry jpaDropListEntry) {
            return Objects.equals(id, jpaDropListEntry.id) &&
                Objects.equals(ownerScope, jpaDropListEntry.ownerScope) &&
                Objects.equals(owner, jpaDropListEntry.owner) &&
                Objects.equals(deniedEntityType, jpaDropListEntry.deniedEntityType) &&
                Objects.equals(deniedEntity, jpaDropListEntry.deniedEntity);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ownerScope, owner, deniedEntityType, deniedEntity);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("ownerScope", ownerScope)
            .add("owner", owner)
            .add("deniedEntityType", deniedEntityType)
            .add("deniedEntity", deniedEntity)
            .toString();
    }

    /**
     * Converts this JPADropListEntry instance to a DropListEntry instance.
     *
     * @return the converted DropListEntry instance
     * @throws IllegalArgumentException if the denied entity cannot be parsed as a MailAddress
     */
    public DropListEntry toDropListEntry() {
        try {
            DropListEntry.Builder builder = DropListEntry.builder();
            switch (OwnerScope.valueOf(this.ownerScope)) {
                case USER -> builder.userOwner(new MailAddress(this.owner));
                case DOMAIN -> builder.domainOwner(Domain.of(this.owner));
                case GLOBAL -> builder.forAll();
            }
            if (DOMAIN.name().equals(this.deniedEntityType)) {
                builder.denyDomain(Domain.of(this.deniedEntity));
            } else {
                builder.denyAddress(new MailAddress(this.deniedEntity));
            }
            return builder.build();
        } catch (AddressException e) {
            throw new IllegalArgumentException("Entity could not be parsed as a MailAddress", e);
        }
    }

    /**
     * Creates a new JPADropListEntry instance from the specified DropListEntry instance.
     *
     * @param dropListEntry the DropListEntry instance to create a JPADropListEntry from
     * @return the created JPADropListEntry instance
     */
    public static JPADropListEntry fromDropListEntry(DropListEntry dropListEntry) {
        JPADropListEntry jpaDropListEntry = new JPADropListEntry();
        jpaDropListEntry.setOwnerScope(dropListEntry.getOwnerScope().name());
        jpaDropListEntry.setOwner(dropListEntry.getOwner());
        jpaDropListEntry.setDeniedEntityType(dropListEntry.getDeniedEntityType().name());
        jpaDropListEntry.setDeniedEntity(dropListEntry.getDeniedEntity());
        return jpaDropListEntry;
    }
}
