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
package org.apache.james.mailbox.store.mail.model.impl;

import org.apache.james.mailbox.store.mail.model.Property;

public final class SimpleProperty implements Property {
    private String namespace;
    private String localName;
    private String value;
    
    /**
     * Construct a property.
     * @param namespace not null
     * @param localName not null
     * @param value not null
     */
    public SimpleProperty(String namespace, String localName, String value) {
        super();
        this.namespace = namespace;
        this.localName = localName;
        this.value = value;
    }
    
    public SimpleProperty(Property property) {
        this(property.getNamespace(), property.getLocalName(), property.getValue());
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Property#getLocalName()
     */
    public String getLocalName() {
        return localName;
    }
    
    /**
     * @see org.apache.james.mailbox.store.mail.model.Property#getNamespace()
     */
    public String getNamespace() {
        return namespace;
    }
    
    /**
     * @see org.apache.james.mailbox.store.mail.model.Property#getValue()
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
    public boolean isNamed(final String namespace, final String localName) {
        return namespace.equals(this.namespace) && localName.equals(this.localName);
    }
    
    /**
     * Is this property in the given namespace?
     * @param namespace not null
     * @return true when this property is in the given namespace,
     * false otherwise
     */
    public boolean isInSpace(final String namespace) {
        return this.namespace.equals(namespace);
    }

    /**
     * Constructs a <code>String</code> with all attributes
     * in name = value format.
     *
     * @return a <code>String</code> representation 
     * of this object.
     */
    public String toString()
    {
        return "SimpleProperty("
        + "namespace='" + this.namespace 
        + "' localName='" + this.localName  
        + "' value='" + this.value 
        + "')";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleProperty)) return false;
        SimpleProperty that = (SimpleProperty) o;
        if (namespace != null ? !namespace.equals(that.namespace) : that.namespace != null) return false;
        if (localName != null ? !localName.equals(that.localName) : that.localName != null) return false;
        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public int hashCode() {
        int result = namespace != null ? namespace.hashCode() : 0;
        result = 31 * result + (localName != null ? localName.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
