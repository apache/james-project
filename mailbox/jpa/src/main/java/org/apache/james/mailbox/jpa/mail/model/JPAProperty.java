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
package org.apache.james.mailbox.jpa.mail.model;

import java.util.Objects;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.openjpa.persistence.jdbc.Index;

@Entity(name = "Property")
@Table(name = "JAMES_MAIL_PROPERTY")
public class JPAProperty {

    /** The system unique key */
    @Id
    @GeneratedValue
    @Column(name = "PROPERTY_ID", nullable = true)
    private long id;

    /** Order within the list of properties */
    @Basic(optional = false)
    @Column(name = "PROPERTY_LINE_NUMBER", nullable = false)
    @Index(name = "INDEX_PROPERTY_LINE_NUMBER")
    private int line;

    /** Local part of the name of this property */
    @Basic(optional = false)
    @Column(name = "PROPERTY_LOCAL_NAME", nullable = false, length = 500)
    private String localName;

    /** Namespace part of the name of this property */
    @Basic(optional = false)
    @Column(name = "PROPERTY_NAME_SPACE", nullable = false, length = 500)
    private String namespace;

    /** Value of this property */
    @Basic(optional = false)
    @Column(name = "PROPERTY_VALUE", nullable = false, length = 1024)
    private String value;

    /**
     * @deprecated enhancement only
     */
    @Deprecated
    public JPAProperty() {
    }

    /**
     * Constructs a property.
     * 
     * @param localName
     *            not null
     * @param namespace
     *            not null
     * @param value
     *            not null
     */
    public JPAProperty(String namespace, String localName, String value, int order) {
        super();
        this.localName = localName;
        this.namespace = namespace;
        this.value = value;
        this.line = order;
    }

    /**
     * Constructs a property cloned from the given.
     * 
     * @param property
     *            not null
     */
    public JPAProperty(Property property, int order) {
        this(property.getNamespace(), property.getLocalName(), property.getValue(), order);
    }

    public Property toProperty() {
        return new Property(namespace, localName, value);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof JPAProperty) {
            JPAProperty that = (JPAProperty) o;

            return Objects.equals(this.id, that.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Constructs a <code>String</code> with all attributes in name = value
     * format.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        return "JPAProperty ( " + "id = " + this.id + " " + "localName = " + this.localName + " "
                + "namespace = " + this.namespace + " " + "value = " + this.value + " )";
    }

}
