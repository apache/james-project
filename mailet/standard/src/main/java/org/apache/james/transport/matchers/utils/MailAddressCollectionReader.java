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

package org.apache.james.transport.matchers.utils;

import java.util.Set;

import javax.mail.internet.AddressException;

import org.apache.mailet.MailAddress;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;

public class MailAddressCollectionReader {

    public static Set<MailAddress> read(String condition) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(condition));
        return FluentIterable.from(Splitter.onPattern("(,| |\t)")
            .split(condition))
            .filter(s -> !Strings.isNullOrEmpty(s))
            .transform(s -> {
                try {
                    return new MailAddress(s);
                } catch (AddressException e) {
                    throw Throwables.propagate(e);
                }
            }).toSet();
    }

}
