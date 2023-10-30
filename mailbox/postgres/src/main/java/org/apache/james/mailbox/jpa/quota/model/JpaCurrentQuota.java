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

package org.apache.james.mailbox.jpa.quota.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;

@Entity(name = "CurrentQuota")
@Table(name = "JAMES_QUOTA_CURRENTQUOTA")
public class JpaCurrentQuota {

    @Id
    @Column(name = "CURRENTQUOTA_QUOTAROOT")
    private String quotaRoot;

    @Column(name = "CURRENTQUOTA_MESSAGECOUNT")
    private long messageCount;

    @Column(name = "CURRENTQUOTA_SIZE")
    private long size;

    public JpaCurrentQuota() {
    }

    public JpaCurrentQuota(String quotaRoot, long messageCount, long size) {
        this.quotaRoot = quotaRoot;
        this.messageCount = messageCount;
        this.size = size;
    }

    public QuotaCountUsage getMessageCount() {
        return QuotaCountUsage.count(messageCount);
    }

    public QuotaSizeUsage getSize() {
        return QuotaSizeUsage.size(size);
    }

    @Override
    public String toString() {
        return "JpaCurrentQuota{" +
            "quotaRoot='" + quotaRoot + '\'' +
            ", messageCount=" + messageCount +
            ", size=" + size +
            '}';
    }
}
