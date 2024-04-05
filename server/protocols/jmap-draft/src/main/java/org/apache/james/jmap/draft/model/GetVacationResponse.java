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

package org.apache.james.jmap.draft.model;

import java.util.List;
import java.util.Objects;

import org.apache.james.jmap.methods.Method;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class GetVacationResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String accountId;
        private VacationResponse vacationResponse;

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder vacationResponse(VacationResponse vacationResponse) {
            this.vacationResponse = vacationResponse;
            return this;
        }

        public GetVacationResponse build() {
            Preconditions.checkArgument(vacationResponse != null, "Account should contain exactly one vacation response");
            return new GetVacationResponse(accountId, vacationResponse);
        }
    }

    private final String accountId;
    private final List<VacationResponse> list;

    private GetVacationResponse(String accountId, VacationResponse vacationResponse) {
        this.accountId = accountId;
        this.list = ImmutableList.of(vacationResponse);
    }

    public String getAccountId() {
        return accountId;
    }

    public List<VacationResponse> getList() {
        return list;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GetVacationResponse that = (GetVacationResponse) o;

        return Objects.equals(this.accountId, that.accountId)
            && Objects.equals(this.list, that.list);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, list);
    }
}
