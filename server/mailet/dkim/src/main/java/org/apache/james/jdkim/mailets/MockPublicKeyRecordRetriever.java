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

package org.apache.james.jdkim.mailets;

import java.util.Collection;
import java.util.List;

import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.exceptions.PermFailException;
import org.apache.james.jdkim.exceptions.TempFailException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public class MockPublicKeyRecordRetriever implements PublicKeyRecordRetriever {
    private final ImmutableMultimap<String, String> records;

    public MockPublicKeyRecordRetriever(String record, CharSequence selector, CharSequence token) {
        records = ImmutableMultimap.of(makeKey(selector, token), record);
    }

    public List<String> getRecords(CharSequence methodAndOptions, CharSequence selector, CharSequence token) throws TempFailException, PermFailException {
        if ("dns/txt".equals(methodAndOptions)) {
            String search = makeKey(selector, token);
            Collection<String> res = this.records.get(search);
            if (res.size() <= 0) {
                throw new TempFailException("Timout or servfail");
            } else {
                return ImmutableList.copyOf(res);
            }
        } else {
            throw new PermFailException("Unsupported method");
        }
    }

    private String makeKey(CharSequence selector, CharSequence token) {
        return selector.toString() + "._domainkey." + token.toString();
    }
}

