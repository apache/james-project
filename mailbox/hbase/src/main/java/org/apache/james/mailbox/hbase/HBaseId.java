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
package org.apache.james.mailbox.hbase;

import java.io.Serializable;
import java.util.UUID;

import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.model.MailboxId;

public class HBaseId implements MailboxId, Serializable {

    private final UUID id;

    public static HBaseId of(UUID id) {
        return new HBaseId(id);
    }

    private HBaseId(UUID id) {
        this.id = id;
    }

    public UUID getRawId() {
        return id;
    }

    @Override
    public String serialize() {
        return id.toString();
    }

    public byte[] toBytes() {
        return Bytes.add(
                    Bytes.toBytes(id.getMostSignificantBits()),
                    Bytes.toBytes(id.getLeastSignificantBits()));
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public int hashCode() {
        return (int) ((id == null) ? 0 : (id.getMostSignificantBits() ^ (id.getMostSignificantBits() >>> 32)));
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
        HBaseId other = (HBaseId) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

}
