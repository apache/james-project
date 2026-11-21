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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaManager;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Quota Response, as defined by RFC 9208 section 5.1: one response per quota root, listing its resources.
 *
 * The resource list can be empty, for instance when no resource of the quota root is limited.
 */
public class QuotaResponse implements ImapResponseMessage {
    public record Resource(String name, Quota<?, ?> quota) {
        public boolean isLimited() {
            return quota.getLimit().isLimited();
        }

        @Override
        public String toString() {
            return name + ' ' + quota.getUsed() + ' ' + quota.getLimit();
        }
    }

    public static QuotaResponse ofLimitedResources(String quotaRoot, QuotaManager.Quotas quotas) {
        return new QuotaResponse(quotaRoot, Stream.of(
                new Resource(ImapConstants.MESSAGE_QUOTA_RESOURCE, quotas.getMessageQuota()),
                new Resource(ImapConstants.STORAGE_QUOTA_RESOURCE, quotas.getStorageQuota()))
            .filter(Resource::isLimited)
            .collect(ImmutableList.toImmutableList()));
    }

    private final String quotaRoot;
    private final ImmutableList<Resource> resources;

    public QuotaResponse(String quotaRoot, List<Resource> resources) {
        this.quotaRoot = quotaRoot;
        this.resources = ImmutableList.copyOf(resources);
    }

    public String getQuotaRoot() {
        return quotaRoot;
    }

    public ImmutableList<Resource> getResources() {
        return resources;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaResponse) {
            QuotaResponse other = (QuotaResponse) o;

            return Objects.equal(this.quotaRoot, other.quotaRoot)
                && Objects.equal(this.resources, other.resources);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(quotaRoot, resources);
    }

    @Override
    public String toString() {
        return ImapConstants.QUOTA_RESPONSE_NAME + ' ' + quotaRoot + ' '
            + resources.stream()
                .map(Resource::toString)
                .collect(Collectors.joining(" ", "(", ")"));
    }
}
