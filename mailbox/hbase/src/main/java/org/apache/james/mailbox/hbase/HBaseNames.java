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
    String MAILBOXES = "JAMES_MAILBOXES";
    byte[] MAILBOXES_TABLE = Bytes.toBytes(MAILBOXES);
    /** Default mailbox column family */
    byte[] MAILBOX_CF = Bytes.toBytes("D");
    /** HBase column qualifiers: field names stored as byte arrays*/
    byte[] MAILBOX_NAME = Bytes.toBytes("name");
    byte[] MAILBOX_USER = Bytes.toBytes("user");
    byte[] MAILBOX_NAMESPACE = Bytes.toBytes("namespace");
    byte[] MAILBOX_LASTUID = Bytes.toBytes("lastUID");
    byte[] MAILBOX_UIDVALIDITY = Bytes.toBytes("uidValidity");
    byte[] MAILBOX_HIGHEST_MODSEQ = Bytes.toBytes("hModSeq");
    byte[] MAILBOX_MESSAGE_COUNT = Bytes.toBytes("count");
    /** The HBase table name for storing subscriptions */
    String SUBSCRIPTIONS = "JAMES_SUBSCRIPTIONS";
    /** The HBase table name for storing subscriptions */
    byte[] SUBSCRIPTIONS_TABLE = Bytes.toBytes(SUBSCRIPTIONS);
    /** Default subscription column family */
    byte[] SUBSCRIPTION_CF = Bytes.toBytes("D");
    /** The HBase table name for storing messages */
    String MESSAGES = "JAMES_MESSAGES";
    /** The HBase table name for storing messages */
    byte[] MESSAGES_TABLE = Bytes.toBytes(MESSAGES);
    /** Column family for storing message meta information*/
    byte[] MESSAGES_META_CF = Bytes.toBytes("M");
    /** Column family for storing message headers*/
    byte[] MESSAGE_DATA_HEADERS_CF = Bytes.toBytes("H");
    /** Column family for storing message body*/
    byte[] MESSAGE_DATA_BODY_CF = Bytes.toBytes("B");
    String PREFIX_META = "m:";
    byte[] PREFIX_META_B = Bytes.toBytes(PREFIX_META);
    /** kept sorted */
    byte[] MESSAGE_BODY_OCTETS = Bytes.toBytes(PREFIX_META + "body");
    byte[] MESSAGE_CONTENT_OCTETS = Bytes.toBytes(PREFIX_META + "content");
    byte[] MESSAGE_INTERNALDATE = Bytes.toBytes(PREFIX_META + "date");
    byte[] MESSAGE_TEXT_LINE_COUNT = Bytes.toBytes(PREFIX_META + "lcount");
    byte[] MESSAGE_MODSEQ = Bytes.toBytes(PREFIX_META + "mseq");
    byte[] MESSAGE_MEDIA_TYPE = Bytes.toBytes(PREFIX_META + "mtype");
    byte[] MESSAGE_SUB_TYPE = Bytes.toBytes(PREFIX_META + "stype");
    byte[] MARKER_PRESENT = Bytes.toBytes("X");
    byte[] MARKER_MISSING = Bytes.toBytes(" ");
    // the maximum recomended HBase column size is 10 MB
    int MAX_COLUMN_SIZE = 1024; //2 * 1024 * 1024;
}
