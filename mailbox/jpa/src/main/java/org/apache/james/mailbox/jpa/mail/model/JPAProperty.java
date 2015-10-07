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

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.openjpa.persistence.jdbc.Index;

@Entity(name = "Property")
@Table(name = "JAMES_MAIL_PROPERTY")
public class JPAProperty implements Property {

    /** The system unique key */
    @Id
    @GeneratedValue
    @Column(name = "PROPERTY_ID", nullable = true)
    // TODO The columnNames are not interpreted, see OPENJPA-223 to fix
    // MAILBOX-186
    @Index(name = "INDEX_PROPERTY_MSG_ID", columnNames = { "MAILBOX_ID", "MAIL_UID" })
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

    /**
     * Create a copy of the give JPAProperty
     * 
     * @param property
     */
    public JPAProperty(JPAProperty property) {
        this(property.getNamespace(), property.getLocalName(), property.getValue(), property.getOrder());
    }

    /**
     * Gets the order of this property.
     * 
     * @return order of this property
     */
    public int getOrder() {
        return line;
    }

    /**
     * Gets the local part of the name of the property.
     * 
     * @return not null
     */
    public String getLocalName() {
        return localName;
    }

    /**
     * Gets the namespace for the name.
     * 
     * @return not null
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Gets the value for this property.
     * 
     * @return not null
     */
    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (id ^ (id >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final JPAProperty other = (JPAProperty) obj;
        if (id != other.id)
            return false;
        return true;
    }

    /**
     * Constructs a <code>String</code> with all attributes in name = value
     * format.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String result = "JPAProperty ( " + "id = " + this.id + " " + "localName = " + this.localName + " "
                + "namespace = " + this.namespace + " " + "value = " + this.value + " )";

        return result;
    }

}
