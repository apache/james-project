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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaManager;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * RFC 9208 section 5.1: a single QUOTA response per quota root, listing its limited resources (possibly none).
 */
public class QuotaResponse implements ImapResponseMessage {
    public static class Resource {
        private final String name;
        private final Quota<?, ?> quota;

        public Resource(String name, Quota<?, ?> quota) {
            this.name = name;
            this.quota = quota;
        }

        public String getName() {
            return name;
        }

        public Quota<?, ?> getQuota() {
            return quota;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Resource) {
                Resource other = (Resource) o;
                return Objects.equal(this.name, other.name)
                    && Objects.equal(this.quota, other.quota);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(name, quota);
        }

        @Override
        public String toString() {
            return name + ' ' + quota.getUsed() + ' ' + quota.getLimit();
        }
    }

    public static QuotaResponse from(String quotaRoot, QuotaManager.Quotas quotas) {
        ImmutableList.Builder<Resource> resources = ImmutableList.builder();
        if (quotas.getMessageQuota().getLimit().isLimited()) {
            resources.add(new Resource(ImapConstants.MESSAGE_QUOTA_RESOURCE, quotas.getMessageQuota()));
        }
        if (quotas.getStorageQuota().getLimit().isLimited()) {
            resources.add(new Resource(ImapConstants.STORAGE_QUOTA_RESOURCE, quotas.getStorageQuota()));
        }
        return new QuotaResponse(quotaRoot, resources.build());
    }

    public static QuotaResponse withoutLimits(String quotaRoot) {
        return new QuotaResponse(quotaRoot, ImmutableList.of());
    }

    private final String quotaRoot;
    private final List<Resource> resources;

    public QuotaResponse(String quotaRoot, List<Resource> resources) {
        this.quotaRoot = quotaRoot;
        this.resources = ImmutableList.copyOf(resources);
    }

    public String getQuotaRoot() {
        return quotaRoot;
    }

    public List<Resource> getResources() {
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
        return ImapConstants.QUOTA_RESPONSE_NAME + ' ' + quotaRoot + " ("
            + resources.stream().map(Resource::toString).collect(Collectors.joining(" ")) + ')';
    }
}
