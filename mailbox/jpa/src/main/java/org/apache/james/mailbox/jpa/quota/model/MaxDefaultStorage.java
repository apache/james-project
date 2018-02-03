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

package org.apache.james.mailbox.jpa.quota.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity(name = "MaxDefaultStorage")
@Table(name = "JAMES_MAX_DEFAULT_STORAGE")
public class MaxDefaultStorage {
    public static final String DEFAULT_KEY = "default_key";
   
    @Id
    @Column(name = "QUOTAROOT_ID")
    private String quotaRoot = DEFAULT_KEY;

    @Column(name = "VALUE")
    private long value;

    public MaxDefaultStorage(long value) {
        this.quotaRoot = DEFAULT_KEY;
        this.value = value;
    }

    public MaxDefaultStorage() {
    }

    public long getValue() {
        return value;
    }
}
