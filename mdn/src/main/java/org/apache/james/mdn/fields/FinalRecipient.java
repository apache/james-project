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

package org.apache.james.mdn.fields;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Preconditions;

/**
 * Implements mandatory Final recipient field
 *
 * https://tools.ietf.org/html/rfc8098#section-3.2.4
 */
public class FinalRecipient implements Field {
    public static final String FIELD_NAME = "Final-Recipient";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<AddressType> addressType;
        private Text finalRecipient;

        private Builder() {
            addressType = Optional.empty();
        }

        public Builder addressType(AddressType addressType) {
            this.addressType = Optional.of(addressType);
            return this;
        }

        public Builder finalRecipient(Text finalRecipient) {
            this.finalRecipient = finalRecipient;
            return this;
        }

        public FinalRecipient build() {
            Preconditions.checkNotNull(finalRecipient);

            return new FinalRecipient(addressType.orElse(AddressType.RFC_822), finalRecipient);
        }
    }

    private final Text finalRecipient;
    private final AddressType addressType;

    private FinalRecipient(AddressType addressType, Text finalRecipient) {
        this.finalRecipient = finalRecipient;
        this.addressType = addressType;
    }

    public Text getFinalRecipient() {
        return finalRecipient;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": " + fieldValue();
    }

    public String fieldValue() {
        return addressType.getType() + "; " + finalRecipient.formatted();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FinalRecipient) {
            FinalRecipient that = (FinalRecipient) o;

            return Objects.equals(finalRecipient, that.finalRecipient)
                && Objects.equals(addressType, that.addressType);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(finalRecipient, addressType);
    }

    @Override
    public String toString() {
        return formattedValue();
    }
}
