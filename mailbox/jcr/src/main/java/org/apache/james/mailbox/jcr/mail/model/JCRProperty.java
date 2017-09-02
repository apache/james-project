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
package org.apache.james.mailbox.jcr.mail.model;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.james.mailbox.jcr.JCRImapConstants;
import org.apache.james.mailbox.jcr.Persistent;
import org.apache.james.mailbox.store.mail.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JCR implementation of {@link Property}
 *
 */
public class JCRProperty implements JCRImapConstants, Persistent, Property {

    private static final Logger LOGGER = LoggerFactory.getLogger(JCRProperty.class);

    private Node node;
    private String namespace;
    private String localName;
    private String value;
    private int order;

    public final static String NAMESPACE_PROPERTY = "jamesMailbox:propertyNamespace";
    public final static String LOCALNAME_PROPERTY =  "jamesMailbox:propertyLocalName";
    public final static String VALUE_PROPERTY =  "jamesMailbox:propertyValue";
    public final static String ORDER_PROPERTY =  "jamesMailbox:propertyOrder";

    public JCRProperty(Node node) {
        this.node = node;
    }

    public JCRProperty(String namespace, String localName, String value) {
        this.namespace = namespace;
        this.localName = localName;
        this.value = value;
    }

    public JCRProperty(Property property) {
        this(property.getNamespace(), property.getLocalName(), property.getValue());
    }
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.mailbox.store.mail.model.AbstractComparableProperty#getOrder
     * ()
     */
    public int getOrder() {
        if (isPersistent()) {
            try {
                return (int)node.getProperty(ORDER_PROPERTY).getLong();
            } catch (RepositoryException e) {
                LOGGER.error("Unable to access Property " + ORDER_PROPERTY, e);
            }
            return 0;
        }
        return order;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.jcr.IsPersistent#getNode()
     */
    public Node getNode() {
        return node;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.Property#getLocalName()
     */
    public String getLocalName() {
        if (isPersistent()) {
            try {
                return node.getProperty(LOCALNAME_PROPERTY).getString();
            } catch (RepositoryException e) {
                LOGGER.error("Unable to access Property " + LOCALNAME_PROPERTY, e);
            }
            return null;
        }
        return localName;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.Property#getNamespace()
     */
    public String getNamespace() {
        if (isPersistent()) {
            try {
                return node.getProperty(NAMESPACE_PROPERTY).getString();
            } catch (RepositoryException e) {
                LOGGER.error("Unable to access Property " + NAMESPACE_PROPERTY, e);
            }
            return null;
        }
        return namespace;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.model.Property#getValue()
     */
    public String getValue() {
        if (isPersistent()) {
            try {
                return node.getProperty(VALUE_PROPERTY).getString();
            } catch (RepositoryException e) {
                LOGGER.error("Unable to access Property " + VALUE_PROPERTY, e);
            }
            return null;
        }
        return value;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.jcr.IsPersistent#isPersistent()
     */
    public boolean isPersistent() {
        return node != null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.jcr.IsPersistent#merge(javax.jcr.Node)
     */
    public void merge(Node node) throws RepositoryException {
        node.setProperty(NAMESPACE_PROPERTY, getNamespace());
        node.setProperty(ORDER_PROPERTY, getOrder());
        node.setProperty(LOCALNAME_PROPERTY, getLocalName());
        node.setProperty(VALUE_PROPERTY, getValue());

        this.node = node;
        /*
        namespace = null;
        order = 0;
        localName = null;
        value = null;
        */
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + getLocalName().hashCode();
        result = PRIME * result + getNamespace().hashCode();
        result = PRIME * result + getValue().hashCode();

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
        final JCRProperty other = (JCRProperty) obj;
        
        if (getLocalName() != null) {
            if (!getLocalName().equals(other.getLocalName()))
        	return false;
        } else {
            if (other.getLocalName() != null)
        	return false;
        }
        if (getNamespace() != null) {
            if (!getNamespace().equals(other.getNamespace()))
        	return false;
        } else {
            if (other.getNamespace() != null)
        	return false;
        }
        if (getValue() != null) {
            if (!getValue().equals(other.getValue()))
        	return false;
        } else {
            if (other.getValue() != null)
        	return false;
        }
        return true;
    }

    /**
     * Constructs a <code>String</code> with all attributes
     * in name = value format.
     *
     * @return a <code>String</code> representation 
     * of this object.
     */
    public String toString() {

        return "Property ( "
            + "localName = " + this.getLocalName() + " "
            + "namespace = " + this.getNamespace() + " "
            + "value = " + this.getValue()
            + " )";
    }
}
