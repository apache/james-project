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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;


public class ListMessagePropertiesAssert {
    private List<InnerProperty> propertiesToInnerProperties(List<Property> properties) {
        return properties.stream()
            .map(propertyToInnerProperty())
            .collect(ImmutableList.toImmutableList());
    }

    private Function<Property, InnerProperty> propertyToInnerProperty() {
        return property -> new InnerProperty(property.getNamespace(), property.getLocalName(), property.getValue());
    }

    public static ListMessagePropertiesAssert assertProperties(List<Property> actual) {
        return new ListMessagePropertiesAssert(actual);
    }

    private final List<Property> actual;

    private ListMessagePropertiesAssert(List<Property> actual) {
        this.actual = actual;
    }

    public void containsOnly(List<Property> expected) {
        assertThat(propertiesToInnerProperties(actual)).containsOnly(propertiesToInnerProperties(expected).toArray(new InnerProperty[0]));
    }
    
    private final class InnerProperty {
        private final String namespace;
        private final String name;
        private final String value;

        public InnerProperty(String namespace, String name, String value) {
            this.namespace = namespace;
            this.name = name;
            this.value = value;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(namespace, name, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InnerProperty) {
                InnerProperty o = (InnerProperty)obj;
                return Objects.equal(namespace, o.getNamespace())
                    && Objects.equal(name, o.getName())
                    && Objects.equal(value, o.getValue());
            }
            return false;
        }
    }
}
