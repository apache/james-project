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

package org.apache.james.imap.message.request;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

/**
 * SETQUOTA request
 */
public class SetQuotaRequest extends AbstractImapRequest {

    public static class ResourceLimit {
        private final String resource;
        private final Long limit;

        public ResourceLimit(String resource, long limit) {
            this.limit = limit;
            this.resource = resource;
        }

        public String getResource() {
            return resource;
        }

        public long getLimit() {
            return limit;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("resource", resource)
                .add("limit", limit)
                .toString();
        }
    }

    private final String quotaRoot;
    private final List<ResourceLimit> resourceLimits;

    public SetQuotaRequest(Tag tag, ImapCommand command, String quotaRoot) {
        super(tag, command);
        this.quotaRoot = quotaRoot;
        this.resourceLimits = new ArrayList<>();
    }

    public void addResourceLimit(String resource, long limit) {
        resourceLimits.add(new ResourceLimit(resource, limit));
    }

    public List<ResourceLimit> getResourceLimits() {
        return ImmutableList.copyOf(resourceLimits);
    }

    public String getQuotaRoot() {
        return quotaRoot;
    }
}
