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

package org.apache.james.utils;

import org.apache.james.mailbox.MailboxManager;

import com.google.common.base.Objects;

public class MailboxManagerDefinition {
    
    private final String name;
    private final MailboxManager manager;

    public MailboxManagerDefinition(String name, MailboxManager manager) {
        this.name = name;
        this.manager = manager;
    }
    
    public MailboxManager getManager() {
        return manager;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(name, manager);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxManagerDefinition) {
            MailboxManagerDefinition other = (MailboxManagerDefinition) obj;
            return Objects.equal(name, other.name) && Objects.equal(manager, other.manager);
        }
        return false;
    }
}