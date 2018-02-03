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
package org.apache.james.mailbox.maildir;

import java.io.Serializable;

import org.apache.james.mailbox.model.MailboxId;

public class MaildirId implements MailboxId, Serializable {

    public static class Factory implements MailboxId.Factory {
        @Override
        public MaildirId fromString(String serialized) {
            return of(Integer.valueOf(serialized));
        }
    }

    public static MaildirId of(int id) {
        return new MaildirId(id);
    }

    private final int id;

    private MaildirId(int id) {
        this.id = id;
    }

    public int getRawId() {
        return id;
    }

    @Override
    public String serialize() {
        return String.valueOf(id);
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MaildirId other = (MaildirId) obj;
        if (id != other.id) {
            return false;
        }
        return true;
    }

}