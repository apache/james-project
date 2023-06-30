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

import java.util.Objects;

import com.google.common.base.MoreObjects;

import reactor.util.annotation.NonNull;

/**
 * <p>Values a namespaced property.</p>
 * <p>
 * The full name of a namespaced property consists of 
 * a local part ({@link #getLocalName()}) and a namespace ({@link #getNamespace()()}). 
 * This is similar - in concept - the local part and namespace of a <code>QName</code>
 * in <abbr title='eXtensible Markup Language'>XML</a>. 
 * </p><p>
 * Conventionally, the namespace
 * is an <abbr title='Uniform Resource Identifier'>URI</abbr> 
 * and the name is simple, leading to a natural mapping into 
 * <abbr title='Resource Description Framework'>RDF</abbr>.
 * For example - namespace "http://james.apache.org/rfc2045",
 * name "Content-Transfer-Encoding", value "BASE64" mapping to
 * predicate "http://james.apache.org/rfc2045#Content-Transfer-Encoding",
 * object "BASE64".
 * </p>
 */
public class Property {
    private final String namespace;
    private final String localName;
    private final String value;

    /**
     * Construct a property.
     * @param namespace not null
     * @param localName not null
     * @param value not null
     */
    public Property(@NonNull String namespace, @NonNull String localName, @NonNull String value) {
        super();
        this.namespace = Objects.requireNonNull(namespace);
        this.localName = Objects.requireNonNull(localName);
        this.value = Objects.requireNonNull(value);
    }

    public Property(Property property) {
        this(property.getNamespace(), property.getLocalName(), property.getValue());
    }

    /**
     * Gets the namespace for the name.
     * @return not null
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Gets the local part of the name of the property.
     * @return not null
     */
    public String getLocalName() {
        return localName;
    }

    /**
     * Gets the value for this property.
     * @return not null
     */
    public String getValue() {
        return value;
    }

    /**
     * Does the full name of the property match that given?
     * @param namespace not null
     * @param localName not null
     * @return true when namespaces and local names match,
     * false otherwise
     */
    public boolean isNamed(String namespace, String localName) {
        return namespace.equals(this.namespace) && localName.equals(this.localName);
    }

    /**
     * Is this property in the given namespace?
     * @param namespace not null
     * @return true when this property is in the given namespace,
     * false otherwise
     */
    public boolean isInSpace(String namespace) {
        return this.namespace.equals(namespace);
    }

    /**
     * Constructs a <code>String</code> with all attributes
     * in name = value format.
     *
     * @return a <code>String</code> representation
     * of this object.
     */
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("namespace", namespace)
            .add("localName", localName)
            .add("value", value)
            .toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Property) {
            Property that = (Property) o;

            return Objects.equals(namespace, that.namespace) &&
                Objects.equals(localName, that.localName) &&
                Objects.equals(value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(namespace, localName, value);
    }
}
