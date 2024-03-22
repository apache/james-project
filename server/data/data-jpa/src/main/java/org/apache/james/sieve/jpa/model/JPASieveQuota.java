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

package org.apache.james.sieve.jpa.model;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.apache.james.core.quota.QuotaSizeLimit;

import com.google.common.base.MoreObjects;

@Entity(name = "JamesSieveQuota")
@Table(name = "JAMES_SIEVE_QUOTA")
@NamedQueries({
        @NamedQuery(name = "findByUsername", query = "SELECT sieveQuota FROM JamesSieveQuota sieveQuota WHERE sieveQuota.username=:username")
})
public class JPASieveQuota {

    @Id
    @Column(name = "USER_NAME", nullable = false, length = 100)
    private String username;

    @Column(name = "SIZE", nullable = false)
    private long size;

    /**
     * @deprecated enhancement only
     */
    @Deprecated
    protected JPASieveQuota() {
    }

    public JPASieveQuota(String username, long size) {
        this.username = username;
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public void setSize(QuotaSizeLimit quotaSize) {
        this.size = quotaSize.asLong();
    }

    public QuotaSizeLimit toQuotaSize() {
        return QuotaSizeLimit.size(size);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JPASieveQuota that = (JPASieveQuota) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("username", username)
                .add("size", size)
                .toString();
    }
}
