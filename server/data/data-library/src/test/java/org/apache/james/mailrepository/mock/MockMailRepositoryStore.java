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

package org.apache.james.mailrepository.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;

public class MockMailRepositoryStore implements MailRepositoryStore {

    final Map<String, MailRepository> m_storedObjectMap = new HashMap<>();

    public void add(String url, MailRepository obj) {
        m_storedObjectMap.put(url, obj);
    }

    @Override
    public MailRepository select(String url) throws MailRepositoryStoreException {
        return get(url);
    }

    private MailRepository get(String key) {
        System.out.println(key);
        return m_storedObjectMap.get(key);
    }

    @Override
    public List<String> getUrls() {
        return new ArrayList<>(m_storedObjectMap.keySet());
    }

}
