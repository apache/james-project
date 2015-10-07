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
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_TYPE_PARAMETER_BOUNDARY_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_CONTENT_TYPE_PARAMETER_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_MEDIA_TYPE_NAME;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_MIME_TYPE_SPACE;
import static org.apache.james.mailbox.store.mail.model.StandardNames.MIME_SUB_TYPE_NAME;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.james.mailbox.store.mail.model.Property;

/**
 * Builds properties
 */
public class PropertyBuilder {
    
    private static final int INITIAL_CAPACITY = 32;

    private Long textualLineCount;
    private final List<SimpleProperty> properties;

    public PropertyBuilder(final List<Property> props) {
        textualLineCount = null;
        properties = new ArrayList<SimpleProperty>(props.size());
        for (final Property property:props) {
            properties.add(new SimpleProperty(property));
        }
    }
    
    public PropertyBuilder() {
        textualLineCount = null;
        properties = new ArrayList<SimpleProperty>(INITIAL_CAPACITY);
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
     * Gets the first value with the given name.
     * Used to retrieve values with a single value.
     * @param namespace not null
     * @param localName not null
     * @return value, 
     * or null when no property has the given name and namespace
     */
    public String getFirstValue(final String namespace, final String localName) {
        String result = null;
        for (SimpleProperty property: properties) {
            if (property.isNamed(namespace, localName)) {
                result = property.getValue();
                break;
            }
        }
        return result;
    }
    
    /**
     * Lists all values for a property.
     * @param namespace not null
     * @param localName not null
     * @return not null
     */
    public List<String> getValues(final String namespace, final String localName) {
        List<String> results = new ArrayList<String>();
        for (SimpleProperty property: properties) {
            if (property.isNamed(namespace, localName)) {
                results.add(property.getValue());
            }
        }
        return results;
    }
    
    /**
     * Sets a property allowing only a single value.
     * @param namespace not null
     * @param localName not null
     * @param value null to remove property
     */
    public void setProperty(final String namespace, final String localName, final String value)
    {
        for (Iterator<SimpleProperty> it= properties.iterator();it.hasNext();) {
            final SimpleProperty property = it.next();
            if (property.isNamed(namespace, localName)) {
                it.remove();
            }
        }
        
        if (value != null) {
            properties.add(new SimpleProperty(namespace, localName, value));
        }
    }
    
    /**
     * Sets a multiple valued property.
     * @param namespace not null
     * @param localName not null
     * @param values null to remove property
     */
    public void setProperty(final String namespace, final String localName, final List<String> values)
    {
        for (Iterator<SimpleProperty> it= properties.iterator();it.hasNext();) {
            final SimpleProperty property = it.next();
            if (property.isNamed(namespace, localName)) {
                it.remove();
            }
        }
        if (values !=null) {
            for(final String value:values) {
                properties.add(new SimpleProperty(namespace, localName, value));
            }
        }
    }
    
    /**
     * Maps properties in the given namespace.
     * @param namespace not null
     * @return values indexed by local name
     */
    public SortedMap<String,String> getProperties(final String namespace) {
        final SortedMap<String, String> parameters = new TreeMap<String, String>();
        for (Iterator<SimpleProperty> it= properties.iterator();it.hasNext();) {
            final SimpleProperty property = it.next();
            if (property.isInSpace(namespace)) {
                parameters.put(property.getLocalName(), property.getValue());
            }
        }
        return parameters;
    }
    
    /**
     * Sets properties in the given namespace from the map.
     * Existing properties in the namespace will be removed.
     * All local names will be converted to lower case.
     * @param namespace not null
     * @param valuesByLocalName not null
     */
    public void setProperties(final String namespace, final Map<String,String> valuesByLocalName) {
        for (Iterator<SimpleProperty> it= properties.iterator();it.hasNext();) {
            final SimpleProperty property = it.next();
            if (property.isInSpace(namespace)) {
                it.remove();
            }
        }
        for (final Map.Entry<String, String> valueByLocalName:valuesByLocalName.entrySet()) {
            properties.add(new SimpleProperty(namespace, valueByLocalName.getKey().toLowerCase(), valueByLocalName.getValue()));
        }
    }
    
    /**
     * Gets the top level MIME content media type.
     * 
     * @return top level MIME content media type, or null if default
     */
    public String getMediaType() {
        return getFirstValue(MIME_MIME_TYPE_SPACE, MIME_MEDIA_TYPE_NAME);
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
     * Gets the MIME content subtype.
     * 
     * @return the MIME content subtype, or null if default
     */
    public String getSubType() {
        return getFirstValue(MIME_MIME_TYPE_SPACE, MIME_SUB_TYPE_NAME);
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
     * Gets the MIME Content-ID.
     * 
     * @return the MIME content subtype, or null if default
     */
    public String getContentID() {
        return getFirstValue(MIME_CONTENT_ID_SPACE, MIME_CONTENT_ID_NAME);
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
     * Gets the MIME Content-Description.
     * 
     * @return the MIME Content-Description, 
     * or null if this meta data is not present
     */
    public String getContentDescription() {
        return getFirstValue(MIME_CONTENT_DESCRIPTION_SPACE, MIME_CONTENT_DESCRIPTION_NAME);
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
     * Gets the MIME Content-Transfer-Encoding.
     * 
     * @return the MIME Content-Transfer-Encoding, 
     * or null if this meta data is not present
     */
    public String getContentTransferEncoding() {
        return getFirstValue(MIME_CONTENT_TRANSFER_ENCODING_SPACE, MIME_CONTENT_TRANSFER_ENCODING_NAME);
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
     * Gets the RFC2557 Content-Location.
     * 
     * @return the RFC2557 Content-Location, 
     * or null if this meta data is not present
     */
    public String getContentLocation() {
        return getFirstValue(MIME_CONTENT_LOCATION_SPACE, MIME_CONTENT_LOCATION_NAME);
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
     * Gets the RFC2183 Content-Disposition disposition-type.
     * 
     * @return the RFC2183 Content-Disposition, 
     * or null if this meta data is not present
     */
    public String getContentDispositionType() {
        return getFirstValue(MIME_CONTENT_DISPOSITION_SPACE, MIME_CONTENT_DISPOSITION_TYPE_NAME);
    }
    
    /**
     * Gets RFC2183 Content-Disposition parameters.
     * @return parameter values indexed by lower case local names 
     */
    public Map<String,String> getContentDispositionParameters() {
        return getProperties(MIME_CONTENT_DISPOSITION_PARAMETER_SPACE);
    }
    
    /**
     * Sets Content-Disposition parameters.
     * Parameter names will be normalised to lower case.
     * @param valuesByParameterName values indexed by parameter name
     */
    public void setContentDispositionParameters(final Map<String,String> valuesByParameterName) {
        setProperties(MIME_CONTENT_DISPOSITION_PARAMETER_SPACE, valuesByParameterName);
    }
    
    /**
     * Gets RFC2045 Content-Type parameters.
     * @return parameter values indexed by lower case local names 
     */
    public Map<String,String> getContentTypeParameters() {
        return getProperties(MIME_CONTENT_TYPE_PARAMETER_SPACE);
    }
    
    /**
     * Sets Content-Type parameters.
     * Parameter names will be normalised to lower case.
     * @param valuesByParameterName values indexed by parameter name
     */
    public void setContentTypeParameters(final Map<String,String> valuesByParameterName) {
        setProperties(MIME_CONTENT_TYPE_PARAMETER_SPACE, valuesByParameterName);
    }
    
    /**
     * Gets the RFC1864 Content-MD5.
     * 
     * @return the RFC1864 Content-MD5, 
     * or null if this meta data is not present
     */
    public String getContentMD5() {
        return getFirstValue(MIME_CONTENT_MD5_SPACE, MIME_CONTENT_MD5_NAME);
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
     * Gets the RFC2045 Content-Type "charset" parameter.
     * 
     * @return the RFC2045 Content-Type "charset" parameter, 
     * or null if this meta data is not present
     */
    public String getCharset() {
        return getFirstValue(MIME_CONTENT_TYPE_PARAMETER_SPACE, MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME);
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
     * Gets the RFC2045 Content-Type "boundary" parameter.
     * 
     * @return the RFC2045 Content-Type "boundary" parameter, 
     * or null if this meta data is not present
     */
    public String getBoundary() {
        return getFirstValue(MIME_CONTENT_TYPE_PARAMETER_SPACE, MIME_CONTENT_TYPE_PARAMETER_BOUNDARY_NAME);
    }
    
    /**
     * Sets RFC2045 Content-Type "boundary" parameter.
     * 
     * @param value the RFC2045 Content-Type "boundary" parameter
     * or null to remove
     */
    public void setBoundary(String value) {
        setProperty(MIME_CONTENT_TYPE_PARAMETER_SPACE, MIME_CONTENT_TYPE_PARAMETER_BOUNDARY_NAME, value);
    }
    
    /**
     * Gets the RFC1766 Content-Language.
     * 
     * @return list of parsed langauge tags from the RFC1766 Content-Language, 
     * possibly empty
     */
    public List<String> getContentLanguage() {
        return getValues(MIME_CONTENT_LANGUAGE_SPACE, MIME_CONTENT_LANGUAGE_NAME);
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
        final List<Property> results = new ArrayList<Property>(properties);
        return results;
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
        return "PropertyBuilder ( "
        + " textualLineCount = " + this.textualLineCount
        + " properties = " + this.properties
        + " )";
    }
}
