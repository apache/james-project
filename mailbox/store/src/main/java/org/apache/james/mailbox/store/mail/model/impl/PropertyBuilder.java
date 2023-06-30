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

import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_DESCRIPTION_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_DESCRIPTION_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_DISPOSITION_PARAMETER_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_DISPOSITION_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_DISPOSITION_TYPE_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_ID_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_ID_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_LANGUAGE_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_LANGUAGE_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_LOCATION_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_LOCATION_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_MD5_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_MD5_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_TRANSFER_ENCODING_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_TRANSFER_ENCODING_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_TYPE_PARAMETER_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_MEDIA_TYPE_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_MIME_TYPE_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_SUB_TYPE_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.james.mailbox.store.mail.model.Property;

/**
 * Builds properties
 */
public class PropertyBuilder {
    private static final int INITIAL_CAPACITY = 32;

    private Long textualLineCount;
    private final List<Property> properties;

    public PropertyBuilder(List<Property> props) {
        textualLineCount = null;
        properties = new ArrayList<>(props.size());
        for (Property property:props) {
            properties.add(new Property(property));
        }
    }
    
    public PropertyBuilder() {
        textualLineCount = null;
        properties = new ArrayList<>(INITIAL_CAPACITY);
    }

    /**
     * Gets the number of CRLF in a textual document.
     * @return CRLF count when document is textual,
     * null otherwise
     */
    public Long getTextualLineCount() {
        return textualLineCount;
    }

    /**
     * Aets the number of CRLF in a textual document.
     * @param textualLineCount count when document is textual,
     * null otherwise
     */
    public void setTextualLineCount(Long textualLineCount) {
        this.textualLineCount = textualLineCount;
    }

    
    /**
     * Sets a property allowing only a single value.
     * @param namespace not null
     * @param localName not null
     * @param value null to remove property
     */
    private void setProperty(String namespace, String localName, String value) {
        properties.removeIf(property -> property.isNamed(namespace, localName));
        
        if (value != null) {
            properties.add(new Property(namespace, localName, value));
        }
    }
    
    /**
     * Sets a multiple valued property.
     * @param namespace not null
     * @param localName not null
     * @param values null to remove property
     */
    private void setProperty(String namespace, String localName, List<String> values) {
        properties.removeIf(property -> property.isNamed(namespace, localName));
        if (values != null) {
            for (String value:values) {
                if (value != null) {
                    properties.add(new Property(namespace, localName, value));
                }
            }
        }
    }
    
    /**
     * Sets properties in the given namespace from the map.
     * Existing properties in the namespace will be removed.
     * All local names will be converted to lower case.
     * @param namespace not null
     * @param valuesByLocalName not null
     */
    private void setProperties(String namespace, Map<String,String> valuesByLocalName) {
        properties.removeIf(property -> property.isInSpace(namespace));
        for (Map.Entry<String, String> valueByLocalName:valuesByLocalName.entrySet()) {
            if (valueByLocalName.getValue() != null) {
                properties.add(new Property(namespace, valueByLocalName.getKey().toLowerCase(Locale.US), valueByLocalName.getValue()));
            }
        }
    }
    
    /**
     * Sets the top level MIME content media type.
     * 
     * @param value top level MIME content media type, 
     * or null to remove
     */
    public void setMediaType(String value) {
        setProperty(MIME_MIME_TYPE_SPACE, MIME_MEDIA_TYPE_NAME, value);
    }
    
    /**
     * Sets the MIME content subtype.
     * 
     * @param value the MIME content subtype, 
     * or null to remove
     */
    public void setSubType(String value) {
        setProperty(MIME_MIME_TYPE_SPACE, MIME_SUB_TYPE_NAME, value);
    }
    
    /**
     * Sets MIME Content-ID.
     * 
     * @param value the MIME content subtype, 
     * or null to remove
     */
    public void setContentID(String value) {
        setProperty(MIME_CONTENT_ID_SPACE, MIME_CONTENT_ID_NAME, value);
    }
    
    /**
     * Sets MIME Content-Description.
     * 
     * @param value the MIME Content-Description
     * or null to remove
     */
    public void setContentDescription(String value) {
        setProperty(MIME_CONTENT_DESCRIPTION_SPACE, MIME_CONTENT_DESCRIPTION_NAME, value);
    }
    
    /**
     * Sets MIME Content-Transfer-Encoding.
     * 
     * @param value the MIME Content-Transfer-Encoding
     * or null to remove
     */
    public void setContentTransferEncoding(String value) {
        setProperty(MIME_CONTENT_TRANSFER_ENCODING_SPACE, MIME_CONTENT_TRANSFER_ENCODING_NAME, value);
    }
    
    /**
     * Sets RFC2557 Content-Location.
     * 
     * @param value the RFC2557 Content-Location
     * or null to remove
     */
    public void setContentLocation(String value) {
        setProperty(MIME_CONTENT_LOCATION_SPACE, MIME_CONTENT_LOCATION_NAME, value);
    }
    
    /**
     * Sets RFC2183 Content-Disposition disposition-type.
     * 
     * @param value the RFC2183 Content-Disposition
     * or null to remove
     */
    public void setContentDispositionType(String value) {
        setProperty(MIME_CONTENT_DISPOSITION_SPACE, MIME_CONTENT_DISPOSITION_TYPE_NAME, value);
    }
    
    /**
     * Sets Content-Disposition parameters.
     * Parameter names will be normalised to lower case.
     * @param valuesByParameterName values indexed by parameter name
     */
    public void setContentDispositionParameters(Map<String,String> valuesByParameterName) {
        setProperties(MIME_CONTENT_DISPOSITION_PARAMETER_SPACE, valuesByParameterName);
    }
    
    /**
     * Sets Content-Type parameters.
     * Parameter names will be normalised to lower case.
     * @param valuesByParameterName values indexed by parameter name
     */
    public void setContentTypeParameters(Map<String,String> valuesByParameterName) {
        setProperties(MIME_CONTENT_TYPE_PARAMETER_SPACE, valuesByParameterName);
    }
    
    /**
     * Sets RFC1864 Content-MD5.
     * 
     * @param value the RFC1864 Content-MD5
     * or null to remove
     */
    public void setContentMD5(String value) {
        setProperty(MIME_CONTENT_MD5_SPACE, MIME_CONTENT_MD5_NAME, value);
    }
    
    /**
     * Sets RFC2045 Content-Type "charset" parameter.
     * 
     * @param value the RFC2045 Content-Type "charset" parameter
     * or null to remove
     */
    public void setCharset(String value) {
        setProperty(MIME_CONTENT_TYPE_PARAMETER_SPACE, MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME, value);
    }
    
    /**
     * Sets RFC1766 Content-Language.
     * 
     * @param values list of parsed language tags from the RFC1766 Content-Language, 
     * possibly empty
     */
    public void setContentLanguage(List<String> values) {
        setProperty(MIME_CONTENT_LANGUAGE_SPACE, MIME_CONTENT_LANGUAGE_NAME, values);
    }
    
    /**
     * Builds a list of properties.
     * @return not null
     */
    public List<Property> toProperties() {
        return new ArrayList<>(properties);
    }

    public Properties build() {
        return new Properties(properties, textualLineCount);
    }

    /**
     * Constructs a <code>String</code> with all attributes
     * in name = value format.
     *
     * @return a <code>String</code> representation 
     * of this object.
     */
    public String toString() {
        return "PropertyBuilder ( "
        + " textualLineCount = " + this.textualLineCount
        + " properties = " + this.properties
        + " )";
    }
}
