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

package org.apache.james.mailbox.store.quota;

import com.google.common.base.Objects;
import org.apache.james.mailbox.model.QuotaRoot;

public class QuotaRootImpl implements QuotaRoot {

    public static QuotaRoot quotaRoot(String value) {
        return new QuotaRootImpl(value);
    }

    private String value;

    private QuotaRootImpl(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof QuotaRoot)) {
            return false;
        }
        return value.equals(((QuotaRoot) o).getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    public String getValue() {
        return value;
    }

}