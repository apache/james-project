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
import java.util.Objects;
import java.util.Optional;

import org.apache.james.imap.api.ImapConstants;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class BodyFetchElement {
    private static final BodyFetchElement rfc822 = new BodyFetchElement(ImapConstants.FETCH_RFC822, SectionType.CONTENT, null, null, null, null);

    private static final BodyFetchElement rfc822Header = new BodyFetchElement(ImapConstants.FETCH_RFC822_HEADER, SectionType.HEADER, null, null, null, null);

    private static final BodyFetchElement rfc822Text = new BodyFetchElement(ImapConstants.FETCH_RFC822_TEXT, SectionType.TEXT, null, null, null, null);

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
    private final SectionType sectionType;
    private final int[] path;
    private final Collection<String> fieldNames;

    public BodyFetchElement(String name, SectionType sectionType, int[] path, Collection<String> fieldNames, Long firstOctet, Long numberOfOctets) {
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
     * @return <code>String</code> collection, when {@link SectionType#HEADER_FIELDS} or
     *         {@link SectionType#HEADER_NOT_FIELDS} or null otherwise
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
     */
    public final SectionType getSectionType() {
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

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BodyFetchElement) {
            BodyFetchElement that = (BodyFetchElement) o;

            return Objects.equals(this.sectionType, that.sectionType)
                && Objects.equals(this.firstOctet, that.firstOctet)
                && Objects.equals(this.numberOfOctets, that.numberOfOctets)
                && Objects.equals(this.name, that.name)
                && Arrays.equals(this.path, that.path)
                && Objects.equals(this.fieldNames, that.fieldNames);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(firstOctet, numberOfOctets, name, sectionType, Arrays.hashCode(path), fieldNames);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("firstOctet", firstOctet)
            .add("numberOfOctets", numberOfOctets)
            .add("name", name)
            .add("sectionType", sectionType)
            .add("fieldNames", Optional.ofNullable(fieldNames).map(ImmutableList::copyOf))
            .toString();
    }
}
