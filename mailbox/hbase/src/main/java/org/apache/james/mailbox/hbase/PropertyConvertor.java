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
package org.apache.james.mailbox.hbase;

import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;

/**
 * Class used for converting message properties to and from byte arrays for use as
 * HBase column qualifiers and values.
 */
public class PropertyConvertor {

    public static final String PREFIX_PROP = "p:";
    public static final byte[] PREFIX_PROP_B = Bytes.toBytes(PREFIX_PROP);
    //TODO: find a better separator.
    /** The separator must not be part of the property namespace or localName */
    private static final String SEPARATOR = "%%";

    /**
     * Returns a byte array that represents a HBase column qualifier for the
     * provided property.
     * @param propNumber the property for storage n HBase
     * @return a byte array that represents a column qualifier for the property
     */
    public static byte[] getQualifier(int propNumber) {
        // allow for about 1000 properties to be stored, we pad them because HBase will store them sorted
        return Bytes.toBytes(PREFIX_PROP + String.format("%03d", propNumber));
    }

    /**
     * Returns a byte array representation of the Property value.
     * (uses Bytes.toBytes)
     * @param prop
     * @return a byte array of the value.
     */
    public static byte[] getValue(Property prop) {
        return Bytes.toBytes(prop.getNamespace() + SEPARATOR + prop.getLocalName() + SEPARATOR + prop.getValue());
    }

    /**
     * Returns a Property from a qualifier byte array.
     * @param value
     * @return a {@link Property}
     * @throws RuntimeException if property prefix or separator is not present
     */
    public static Property getProperty(byte[] value) {
        String ns = Bytes.toString(value);
        //TODO: we assume the SEPARATOR=%% can not appear in a normal property. This may not be true.
        String[] parts = ns.split(SEPARATOR);
        if (parts.length != 3) {
            throw new RuntimeException("Separator not found in qualifier "
                    + Bytes.toString(value));
        }
        return new SimpleProperty(parts[0], parts[1], parts[2]);
    }
}
