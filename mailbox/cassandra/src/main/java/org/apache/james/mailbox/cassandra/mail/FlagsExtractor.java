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
package org.apache.james.mailbox.cassandra.mail;

import java.util.Set;

import javax.mail.Flags;

import org.apache.james.mailbox.cassandra.table.Flag;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.TypeTokens;
import com.google.common.reflect.TypeToken;

public class FlagsExtractor {
    public static final TypeToken<Set<String>> STRING_SET = TypeTokens.setOf(String.class);

    public static Flags getFlags(Row row) {
        Flags flags = new Flags();
        for (String flag : Flag.ALL) {
            if (row.getBool(flag)) {
                flags.add(Flag.JAVAX_MAIL_FLAG.get(flag));
            }
        }
        row.get(Flag.USER_FLAGS, STRING_SET)
            .forEach(flags::add);
        return flags;
    }

    public static Flags getApplicableFlags(Row row) {
        Flags flags = new Flags();
        row.get(Flag.USER_FLAGS, STRING_SET)
            .forEach(flags::add);
        return flags;
    }
}
