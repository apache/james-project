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

package org.apache.james.imap.message.response;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.model.Quota;

import com.google.common.base.Objects;

/**
 * Quota Response
 */
public class QuotaResponse implements ImapResponseMessage {
    private final String resourceName;
    private final String quotaRoot;
    private final Quota<?, ?> quota;

    public QuotaResponse(String resource, String quotaRoot, Quota<?, ?> quota) {
        this.quota = quota;
        this.resourceName = resource;
        this.quotaRoot = quotaRoot;
    }

    public Quota<?, ?> getQuota() {
        return quota;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getQuotaRoot() {
        return quotaRoot;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaResponse) {
            QuotaResponse other = (QuotaResponse) o;

            return Objects.equal(this.quotaRoot, other.quotaRoot)
                && Objects.equal(this.resourceName, other.resourceName)
                && Objects.equal(this.quota, other.quota);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(resourceName, quotaRoot, quota);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder()
                .append(ImapConstants.QUOTA_RESPONSE_NAME)
                .append(' ')
                .append(quotaRoot)
                .append(' ')
                .append('(')
                .append(resourceName)
                .append(' ')
                .append(quota.getUsed())
                .append(' ')
                .append(quota.getLimit())
                .append(')');
        return result.toString();
    }

}
