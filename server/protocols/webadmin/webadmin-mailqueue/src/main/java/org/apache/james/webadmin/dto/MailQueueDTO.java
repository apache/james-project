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

package org.apache.james.webadmin.dto;

import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.ManageableMailQueue;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class MailQueueDTO {

    public static Builder builder() {
        return new Builder();
    }

    public static MailQueueDTO from(ManageableMailQueue mailQueue) throws MailQueueException {
        return builder()
            .name(mailQueue.getName().asString())
            .size(mailQueue.getSize())
            .build();
    }

    public static class Builder {

        private String name;
        private long size;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public MailQueueDTO build() {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is mandatory");
            return new MailQueueDTO(name, size);
        }
    }

    private final String name;
    private final long size;

    private MailQueueDTO(String name, long size) {
        this.name = name;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }
}
