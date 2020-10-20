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
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.james.mailbox.store.mail.model.Property;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class Properties {
    private Long textualLineCount;
    private final ImmutableList<Property> properties;

    public Properties(List<Property> props, Long textualLineCount) {
        this.textualLineCount = textualLineCount;
        this.properties = ImmutableList.copyOf(props);
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
     * Gets the first value with the given name.
     * Used to retrieve values with a single value.
     * @param namespace not null
     * @param localName not null
     * @return value, 
     * or null when no property has the given name and namespace
     */
    private String getFirstValue(String namespace, String localName) {
        return properties.stream()
            .filter(property -> property.isNamed(namespace, localName))
            .findFirst()
            .map(Property::getValue)
            .orElse(null);
    }
    
    /**
     * Lists all values for a property.
     * @param namespace not null
     * @param localName not null
     * @return not null
     */
    private List<String> getValues(String namespace, String localName) {
        return properties.stream()
            .filter(property -> property.isNamed(namespace, localName))
            .map(Property::getValue)
            .collect(Guavate.toImmutableList());
    }

    
    /**
     * Maps properties in the given namespace.
     * @param namespace not null
     * @return values indexed by local name
     */
    private SortedMap<String,String> getProperties(String namespace) {
        final SortedMap<String, String> parameters = new TreeMap<>();
        for (Property property : properties) {
            if (property.isInSpace(namespace)) {
                parameters.put(property.getLocalName(), property.getValue());
            }
        }
        return parameters;
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
     * Gets the MIME content subtype.
     * 
     * @return the MIME content subtype, or null if default
     */
    public String getSubType() {
        return getFirstValue(MIME_MIME_TYPE_SPACE, MIME_SUB_TYPE_NAME);
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
     * Gets the MIME Content-Description.
     * 
     * @return the MIME Content-Description, 
     * or null if this meta data is not present
     */
    public String getContentDescription() {
        return getFirstValue(MIME_CONTENT_DESCRIPTION_SPACE, MIME_CONTENT_DESCRIPTION_NAME);
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
     * Gets the RFC2557 Content-Location.
     * 
     * @return the RFC2557 Content-Location, 
     * or null if this meta data is not present
     */
    public String getContentLocation() {
        return getFirstValue(MIME_CONTENT_LOCATION_SPACE, MIME_CONTENT_LOCATION_NAME);
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
     * Gets RFC2045 Content-Type parameters.
     * @return parameter values indexed by lower case local names 
     */
    public Map<String,String> getContentTypeParameters() {
        return getProperties(MIME_CONTENT_TYPE_PARAMETER_SPACE);
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
     * Gets the RFC2045 Content-Type "charset" parameter.
     * 
     * @return the RFC2045 Content-Type "charset" parameter, 
     * or null if this meta data is not present
     */
    public String getCharset() {
        return getFirstValue(MIME_CONTENT_TYPE_PARAMETER_SPACE, MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME);
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

    public List<Property> toProperties() {
        return new ArrayList<>(properties);
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
            .add("textualLineCount", textualLineCount)
            .add("properties", properties)
            .toString();
    }
}
