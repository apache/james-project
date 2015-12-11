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

package org.apache.james.jmap.methods;

import static com.google.common.base.Objects.toStringHelper;

import java.util.Objects;

import org.apache.james.mailbox.MailboxSession;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;

public interface Method {

    interface Request {

        public static Name name(String name) {
            return new Name(name);
        }
        
        public class Name {

            private final String name;
            
            private Name(String name) {
                Preconditions.checkNotNull(name);
                Preconditions.checkArgument(!name.isEmpty());
                this.name = name;
            }

            @JsonValue
            public String getName() {
                return name;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Name) {
                    Name other = (Name) obj;
                    return Objects.equals(name, other.name);
                }
                return false;
            }

            @Override
            public int hashCode() {
                return Objects.hash(name);
            }

            @Override
            public String toString() {
                return toStringHelper(this).add("name", name).toString();
            }
        }

    }
    
    interface Response {

        public static Name name(String name) {
            return new Name(name);
        }
        
        public class Name {

            private final String name;

            protected Name(String name) {
                Preconditions.checkNotNull(name);
                Preconditions.checkArgument(!name.isEmpty());
                this.name = name;
            }

            @JsonValue
            public String getName() {
                return name;
            }
            
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Name) {
                    Name other = (Name) obj;
                    return Objects.equals(name, other.name);
                }
                return false;
            }
            
            @Override
            public int hashCode() {
                return Objects.hash(name);
            }
            
            @Override
            public String toString() {
                return toStringHelper(this).add("name", name).toString();
            }
        }
    };


    Request.Name requestHandled();

    Response.Name responseName();
    
    Class<? extends JmapRequest> requestType();
    
    Response process(JmapRequest request, MailboxSession mailboxSession);

}
