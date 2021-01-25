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

package org.apache.james.mailbox.events;

import java.util.Objects;

import org.apache.james.events.Group;

public class GenericGroup extends Group {
    public static final String DELIMITER = "-";
    private final String groupName;

    public GenericGroup(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public String asString() {
        return super.asString() + DELIMITER + groupName;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof GenericGroup) {
            GenericGroup that = (GenericGroup) o;

            return Objects.equals(this.groupName, that.groupName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(groupName);
    }
}
