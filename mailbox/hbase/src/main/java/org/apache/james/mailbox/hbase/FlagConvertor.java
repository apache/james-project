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

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * Class used for converting message flags to and from byte arrays for use as
 * HBase column qualifiers.
 */
public class FlagConvertor {

    public static final String PREFIX_SFLAGS = "sf:";
    public static final byte[] PREFIX_SFLAGS_B = Bytes.toBytes(PREFIX_SFLAGS);
    public static final String PREFIX_UFLAGS = "uf:";
    public static final byte[] PREFIX_UFLAGS_B = Bytes.toBytes(PREFIX_UFLAGS);
    /*TODO: find a way to store all flags as a single byte (HBase bitewise operations). 
     * this will be efficient also because we will not store the column names.
     */
    public static final byte[] FLAGS_ANSWERED = Bytes.toBytes("sf:A");
    public static final byte[] FLAGS_DELETED = Bytes.toBytes("sf:DE");
    public static final byte[] FLAGS_DRAFT = Bytes.toBytes("sf:DR");
    public static final byte[] FLAGS_FLAGGED = Bytes.toBytes("sf:F");
    public static final byte[] FLAGS_RECENT = Bytes.toBytes("sf:R");
    public static final byte[] FLAGS_SEEN = Bytes.toBytes("sf:S");
    public static final byte[] FLAGS_USER = Bytes.toBytes("sf:U");

    /**
     * Converts a {@link javax.mail.Flags.Flag} to a byte array representation
     * used for storing in HBase (as a column qualifier).
     * @param flag
     * @return a byte representation of the flag.
     * 
     * Throws RuntimeException if Flag is not recognized. 
     */
    public static byte[] systemFlagToBytes(Flags.Flag flag) {
        if (flag.equals(Flag.ANSWERED)) {
            return FLAGS_ANSWERED;
        }
        if (flag.equals(Flag.DELETED)) {
            return FLAGS_DELETED;
        }
        if (flag.equals(Flag.DRAFT)) {
            return FLAGS_DRAFT;
        }
        if (flag.equals(Flag.FLAGGED)) {
            return FLAGS_FLAGGED;
        }
        if (flag.equals(Flag.RECENT)) {
            return FLAGS_RECENT;
        }
        if (flag.equals(Flag.SEEN)) {
            return FLAGS_SEEN;
        }
        if (flag.equals(Flag.USER)) {
            return FLAGS_USER;
        }
        throw new RuntimeException("Invalid Flag supplied");
    }

    /**
     * Returns a {@link javax.mail.Flags.Flag} coresponding to the supplyed 
     * byte array. 
     * @param bytes byte array representation
     * @return one of {@link javax.mail.Flags.Flag}
     * @throws RuntimeException if the byte array does not match a 
     * suitable represetnation.
     */
    public static Flag systemFlagFromBytes(byte[] bytes) {
        if (Bytes.equals(bytes, FLAGS_ANSWERED)) {
            return Flag.ANSWERED;
        }
        if (Bytes.equals(bytes, FLAGS_DELETED)) {
            return Flag.DELETED;
        }
        if (Bytes.equals(bytes, FLAGS_DRAFT)) {
            return Flag.DRAFT;
        }
        if (Bytes.equals(bytes, FLAGS_FLAGGED)) {
            return Flag.FLAGGED;
        }
        if (Bytes.equals(bytes, FLAGS_RECENT)) {
            return Flag.RECENT;
        }
        if (Bytes.equals(bytes, FLAGS_SEEN)) {
            return Flag.SEEN;
        }
        if (Bytes.equals(bytes, FLAGS_USER)) {
            return Flag.USER;
        }
        throw new RuntimeException("This is not a recognized system flag: " + Bytes.toString(bytes));
    }

    /**
     * Converts a user flag to a byte array for use as a HBase column qualifier.
     * @param flag user flag to convert
     * @return a byte array representation of the user flag
     */
    public static byte[] userFlagToBytes(String flag) {
        return Bytes.toBytes(PREFIX_UFLAGS + flag);
    }

    /**
     * Converts a byte array to a user flag.
     * @param bytes the user flag byte representation
     * @return a {@link String} representaion of the user flag
     * @throws RuntimeException if the user flag prefix is not found.
     */
    public static String userFlagFromBytes(byte[] bytes) {
        if (Bytes.startsWith(bytes, PREFIX_UFLAGS_B)) {
            return Bytes.toString(bytes, PREFIX_UFLAGS_B.length, bytes.length - PREFIX_UFLAGS_B.length);
        }
        throw new RuntimeException("This is not a user flag representation: " + Bytes.toString(bytes));
    }
}
