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

/**
 * Contains table names, column family names, qualifier names and other constants
 * for use in HBase.
 *
 * Each qualifier in the META column will begin with a short prefix that will
 * determine it's purpose. </br>
 * Qualifier prefix meaning:<br/>
 * <ul>
 * <li> m: meta information; </li>
 * <li> sf: system flag (DELETE, RECENT, etc.) </li>
 * <li> uf: user flag </li>
 * <li> p: user property</li>
 * </ul>
 */
public interface HBaseNames {

    /**
     * The HBase table name for storing mailbox names
     */
    public static final String MAILBOXES = "JAMES_MAILBOXES";
    public static final byte[] MAILBOXES_TABLE = Bytes.toBytes(MAILBOXES);
    /** Default mailbox column family */
    public static final byte[] MAILBOX_CF = Bytes.toBytes("D");
    /** HBase column qualifiers: field names stored as byte arrays*/
    public static final byte[] MAILBOX_NAME = Bytes.toBytes("name");
    public static final byte[] MAILBOX_USER = Bytes.toBytes("user");
    public static final byte[] MAILBOX_NAMESPACE = Bytes.toBytes("namespace");
    public static final byte[] MAILBOX_LASTUID = Bytes.toBytes("lastUID");
    public static final byte[] MAILBOX_UIDVALIDITY = Bytes.toBytes("uidValidity");
    public static final byte[] MAILBOX_HIGHEST_MODSEQ = Bytes.toBytes("hModSeq");
    public static final byte[] MAILBOX_MESSAGE_COUNT = Bytes.toBytes("count");
    /** The HBase table name for storing subscriptions */
    public static final String SUBSCRIPTIONS = "JAMES_SUBSCRIPTIONS";
    /** The HBase table name for storing subscriptions */
    public static final byte[] SUBSCRIPTIONS_TABLE = Bytes.toBytes(SUBSCRIPTIONS);
    /** Default subscription column family */
    public static final byte[] SUBSCRIPTION_CF = Bytes.toBytes("D");
    /** The HBase table name for storing messages */
    public static final String MESSAGES = "JAMES_MESSAGES";
    /** The HBase table name for storing messages */
    public static final byte[] MESSAGES_TABLE = Bytes.toBytes(MESSAGES);
    /** Column family for storing message meta information*/
    public static final byte[] MESSAGES_META_CF = Bytes.toBytes("M");
    /** Column family for storing message headers*/
    public static final byte[] MESSAGE_DATA_HEADERS_CF = Bytes.toBytes("H");
    /** Column family for storing message body*/
    public static final byte[] MESSAGE_DATA_BODY_CF = Bytes.toBytes("B");
    public static final String PREFIX_META = "m:";
    public static final byte[] PREFIX_META_B = Bytes.toBytes(PREFIX_META);
    /** kept sorted */
    public static final byte[] MESSAGE_BODY_OCTETS = Bytes.toBytes(PREFIX_META + "body");
    public static final byte[] MESSAGE_CONTENT_OCTETS = Bytes.toBytes(PREFIX_META + "content");
    public static final byte[] MESSAGE_INTERNALDATE = Bytes.toBytes(PREFIX_META + "date");
    public static final byte[] MESSAGE_TEXT_LINE_COUNT = Bytes.toBytes(PREFIX_META + "lcount");
    public static final byte[] MESSAGE_MODSEQ = Bytes.toBytes(PREFIX_META + "mseq");
    public static final byte[] MESSAGE_MEDIA_TYPE = Bytes.toBytes(PREFIX_META + "mtype");
    public static final byte[] MESSAGE_SUB_TYPE = Bytes.toBytes(PREFIX_META + "stype");
    public static final byte[] MARKER_PRESENT = Bytes.toBytes("X");
    public static final byte[] MARKER_MISSING = Bytes.toBytes(" ");
    // the maximum recomended HBase column size is 10 MB
    public static final int MAX_COLUMN_SIZE = 1024; //2 * 1024 * 1024;
}
