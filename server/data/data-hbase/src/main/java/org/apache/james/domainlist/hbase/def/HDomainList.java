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
package org.apache.james.domainlist.hbase.def;

import org.apache.hadoop.hbase.util.Bytes;

/**
 * Definitions for the DomainList HBase Table.
 * 
 * Contains the table name, column family name and
 * the used column/qualifier names.
 */
public interface HDomainList {

    byte[] TABLE_NAME = Bytes.toBytes("JAMES_DOMAINLIST");
    byte[] COLUMN_FAMILY_NAME = Bytes.toBytes("JAMES_DOMAINLIST");
    
    public interface COLUMN {
        byte [] DOMAIN = Bytes.toBytes("dom");
    }

}
