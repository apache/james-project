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

package org.apache.james.events;

import java.util.Objects;

import com.google.common.base.Preconditions;

public class DispatchingFailureGroup extends Group {
    public static final String DELIMITER = "-";

    public static DispatchingFailureGroup from(String value) {
        Preconditions.checkArgument(value.startsWith(DispatchingFailureGroup.class.getName() + DELIMITER));
        return new DispatchingFailureGroup(new EventBusName(value.substring(value.indexOf(DELIMITER) + 1)));
    }

    private final EventBusName eventBusName;

    public DispatchingFailureGroup(EventBusName eventBusName) {
        this.eventBusName = eventBusName;
    }

    public EventBusName getEventBusName() {
        return eventBusName;
    }

    @Override
    public String asString() {
        return super.asString() + DELIMITER + eventBusName.value();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DispatchingFailureGroup) {
            DispatchingFailureGroup that = (DispatchingFailureGroup) o;
            return Objects.equals(this.eventBusName, that.eventBusName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(super.hashCode(), eventBusName);
    }
}
