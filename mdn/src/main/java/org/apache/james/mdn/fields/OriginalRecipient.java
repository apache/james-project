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
 * Implements optional Original-Recipient field defined in:
 *
 * https://tools.ietf.org/html/rfc8098#section-3.2.3
 */
public class OriginalRecipient implements Field {
    private static final String FIELD_NAME = "Original-Recipient";

    public static OriginalRecipient ofUnknown(Text address) {
        return new OriginalRecipient(AddressType.UNKNOWN, address);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<AddressType> addressType;
        private Text originalRecipient;

        private Builder() {
            addressType = Optional.empty();
        }

        public Builder addressType(AddressType addressType) {
            this.addressType = Optional.of(addressType);
            return this;
        }

        public Builder originalRecipient(Text originalRecipient) {
            this.originalRecipient = originalRecipient;
            return this;
        }

        public OriginalRecipient build() {
            Preconditions.checkNotNull(originalRecipient);

            return new OriginalRecipient(addressType.orElse(AddressType.RFC_822), originalRecipient);
        }
    }

    private final Text originalRecipient;
    private final AddressType addressType;

    private OriginalRecipient(AddressType addressType, Text originalRecipient) {
        this.addressType = addressType;
        this.originalRecipient = originalRecipient;
    }

    public Text getOriginalRecipient() {
        return originalRecipient;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof OriginalRecipient) {
            OriginalRecipient that = (OriginalRecipient) o;

            return Objects.equals(this.originalRecipient, that.originalRecipient)
                && Objects.equals(this.addressType, that.addressType);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(originalRecipient, addressType);
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": " + fieldValue();
    }

    public String fieldValue() {
        return addressType.getType() + "; " + originalRecipient.formatted();
    }

    @Override
    public String toString() {
        return formattedValue();
    }
}
