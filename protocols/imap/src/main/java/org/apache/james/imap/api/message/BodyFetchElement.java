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
package org.apache.james.imap.api.message;

import java.util.Arrays;
import java.util.Collection;

import org.apache.james.imap.api.ImapConstants;

public class BodyFetchElement {

    public static final int TEXT = 0;

    public static final int MIME = 1;

    public static final int HEADER = 2;

    public static final int HEADER_FIELDS = 3;

    public static final int HEADER_NOT_FIELDS = 4;

    public static final int CONTENT = 5;

    private static final BodyFetchElement rfc822 = new BodyFetchElement(ImapConstants.FETCH_RFC822, CONTENT, null, null, null, null);

    private static final BodyFetchElement rfc822Header = new BodyFetchElement(ImapConstants.FETCH_RFC822_HEADER, HEADER, null, null, null, null);

    private static final BodyFetchElement rfc822Text = new BodyFetchElement(ImapConstants.FETCH_RFC822_TEXT, TEXT, null, null, null, null);

    public static final BodyFetchElement createRFC822() {
        return rfc822;
    }

    public static final BodyFetchElement createRFC822Header() {
        return rfc822Header;
    }

    public static final BodyFetchElement createRFC822Text() {
        return rfc822Text;
    }

    private final Long firstOctet;

    private final Long numberOfOctets;

    private final String name;

    private final int sectionType;

    private final int[] path;

    private final Collection<String> fieldNames;

    public BodyFetchElement(final String name, final int sectionType, final int[] path, final Collection<String> fieldNames, Long firstOctet, Long numberOfOctets) {
        this.name = name;
        this.sectionType = sectionType;
        this.fieldNames = fieldNames;
        this.path = path;
        this.firstOctet = firstOctet;
        this.numberOfOctets = numberOfOctets;
    }

    public String getResponseName() {
        return this.name;
    }

    /**
     * Gets field names.
     * 
     * @return <code>String</code> collection, when {@link #HEADER_FIELDS} or
     *         {@link #HEADER_NOT_FIELDS} or null otherwise
     */
    public final Collection<String> getFieldNames() {
        return fieldNames;
    }

    /**
     * Gets the MIME path.
     * 
     * @return the path, or null if the section is the base message
     */
    public final int[] getPath() {
        return path;
    }

    /**
     * Gets the type of section.
     * 
     * @return {@link #HEADER_FIELDS}, {@link #TEXT}, {@link #CONTENT},
     *         {@link #HEADER}, {@link #MIME} or {@link #HEADER_NOT_FIELDS}
     */
    public final int getSectionType() {
        return sectionType;
    }

    /**
     * Gets the first octet for a partial fetch.
     * 
     * @return the firstOctet when this is a partial fetch or null
     */
    public final Long getFirstOctet() {
        return firstOctet;
    }

    /**
     * For a partial fetch, gets the number of octets to be returned.
     * 
     * @return the lastOctet, or null
     */
    public final Long getNumberOfOctets() {
        return numberOfOctets;
    }

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((fieldNames == null) ? 0 : fieldNames.hashCode());
        result = PRIME * result + ((firstOctet == null) ? 0 : firstOctet.hashCode());
        result = PRIME * result + ((name == null) ? 0 : name.hashCode());
        result = PRIME * result + ((numberOfOctets == null) ? 0 : numberOfOctets.hashCode());
        result = PRIME * result + ((path == null) ? 0 : path.length);
        result = PRIME * result + sectionType;
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final BodyFetchElement other = (BodyFetchElement) obj;
        if (fieldNames == null) {
            if (other.fieldNames != null)
                return false;
        } else if (!fieldNames.equals(other.fieldNames))
            return false;
        if (firstOctet == null) {
            if (other.firstOctet != null)
                return false;
        } else if (!firstOctet.equals(other.firstOctet))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (numberOfOctets == null) {
            if (other.numberOfOctets != null)
                return false;
        } else if (!numberOfOctets.equals(other.numberOfOctets))
            return false;
        if (!Arrays.equals(path, other.path))
            return false;
        if (sectionType != other.sectionType)
            return false;
        return true;
    }

}
