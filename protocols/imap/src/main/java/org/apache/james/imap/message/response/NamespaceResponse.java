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
import java.util.Objects;

import org.apache.james.imap.api.message.response.ImapResponseMessage;

/**
 * Describes a NAMESPACE response.
 */
public class NamespaceResponse implements ImapResponseMessage {
    private final List<Namespace> personal;
    private final List<Namespace> users;
    private final List<Namespace> shared;

    public NamespaceResponse(List<Namespace> personal, List<Namespace> users, List<Namespace> shared) {
        this.personal = personal;
        this.users = users;
        this.shared = shared;
    }

    /**
     * Gets the personal namespace.
     * 
     * @return possibly null
     */
    public List<Namespace> getPersonal() {
        return personal;
    }

    /**
     * Gets shared namespaces.
     * 
     * @return possibly null
     */
    public List<Namespace> getShared() {
        return shared;
    }

    /**
     * Gets the namespaces for other users.
     * 
     * @return possibly null
     */
    public List<Namespace> getUsers() {
        return users;
    }

    /**
     * Describes a namespace.
     */
    public static final class Namespace {
        private final String prefix;
        private final char delimiter;

        public Namespace(String prefix, char delimiter) {
            this.prefix = prefix;
            this.delimiter = delimiter;
        }

        /**
         * Gets the delimiter used to separate mailboxes.
         * 
         * @return not null
         */
        public char getDelimiter() {
            return delimiter;
        }

        /**
         * Gets the leading prefix used by this namespace.
         * 
         * @return not null
         */
        public String getPrefix() {
            return prefix;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Namespace) {
                Namespace namespace = (Namespace) o;

                return Objects.equals(this.delimiter, namespace.delimiter)
                    && Objects.equals(this.prefix, namespace.prefix);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(prefix, delimiter);
        }

        @Override
        public String toString() {
            return "Namespace [prefix=" + prefix + ", delim=" + delimiter + "]";
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof NamespaceResponse) {
            NamespaceResponse that = (NamespaceResponse) o;

            return Objects.equals(this.personal, that.personal)
                && Objects.equals(this.users, that.users)
                && Objects.equals(this.shared, that.shared);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(personal, users, shared);
    }

    /**
     * Renders object suitably for logging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        return "NamespaceResponse [personal = " + this.personal + " users = " + this.users + " shared = " + this.shared + "  ]";
    }
}
