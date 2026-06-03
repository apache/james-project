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

import org.apache.james.mailbox.events.GenericGroup;

import com.google.common.base.Strings;

public class Group {
    public static class GroupDeserializationException extends Exception {
        GroupDeserializationException(String message) {
            super(message);
        }

        GroupDeserializationException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * A {@link Group} identified solely by its serialized form.
     *
     * Groups are encoded as a plain String, historically the fully qualified name of the concrete
     * {@link Group} subclass. Deserialization purposely no longer instantiates that class: requiring the
     * class to be loadable broke the reprocessing of dead-lettered events whose Group is defined in an
     * extension that is not part of the default class path (extensions are loaded in a dedicated class
     * loader). Since {@link Group} identity (see {@link #equals(Object)} / {@link #hashCode()}) relies on
     * {@link #asString()}, a {@code DeserializedGroup} is interchangeable with the concrete instance
     * registered by the running extension, both for lookups in registration maps and for redelivery.
     */
    private static class DeserializedGroup extends Group {
        private final String value;

        private DeserializedGroup(String value) {
            this.value = value;
        }

        @Override
        public String asString() {
            return value;
        }
    }

    public static Group deserialize(String serializedGroup) throws GroupDeserializationException {
        if (Strings.isNullOrEmpty(serializedGroup)) {
            throw new GroupDeserializationException("A serialized group can not be null or empty");
        }
        try {
            if (serializedGroup.startsWith(GenericGroup.class.getName() + GenericGroup.DELIMITER)) {
                return new GenericGroup(serializedGroup.substring(GenericGroup.class.getName().length() + 1));
            }
            if (serializedGroup.startsWith(DispatchingFailureGroup.class.getName() + DispatchingFailureGroup.DELIMITER)) {
                return DispatchingFailureGroup.from(serializedGroup);
            }
            if (!serializedGroup.contains(".")) {
                throw new GroupDeserializationException("A serialized group must be a fully qualified name: " + serializedGroup);
            }
            return new DeserializedGroup(serializedGroup);
        } catch (GroupDeserializationException e) {
            throw e;
        } catch (Exception e) {
            throw new GroupDeserializationException(e);
        }
    }

    public String asString() {
        return getClass().getName();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Group) {
            Group group = (Group) o;
            return Objects.equals(asString(), group.asString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(asString());
    }
}
