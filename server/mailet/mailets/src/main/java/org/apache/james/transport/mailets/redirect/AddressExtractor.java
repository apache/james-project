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

package org.apache.james.transport.mailets.redirect;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.core.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class AddressExtractor {

    private static final boolean ENFORCE_RFC822_SYNTAX = false;

    public static Builder withContext(MailetContext mailetContext) {
        return new Builder(mailetContext);
    }

    public static class Builder {

        private MailetContext mailetContext;
        private List<String> allowedSpecials;

        public Builder(MailetContext mailetContext) {
            this.mailetContext = mailetContext;
        }

        public Builder allowedSpecials(List<String> allowedSpecials) {
            this.allowedSpecials = allowedSpecials;
            return this;
        }

        public List<MailAddress> extract(Optional<String> addressList) throws MessagingException {
            checkParameters();
            return new AddressExtractor(mailetContext, allowedSpecials).extract(addressList);
        }

        public Optional<MailAddress> getSpecialAddress(String addressString) throws MessagingException {
            checkParameters();
            return new AddressExtractor(mailetContext, allowedSpecials).getSpecialAddress(addressString);
        }

        private void checkParameters() {
            Preconditions.checkNotNull(mailetContext, "'mailetContext' is mandatory");
            Preconditions.checkNotNull(allowedSpecials, "'allowedSpecials' is mandatory");
        }
    }

    private final MailetContext mailetContext;
    private final List<String> allowedSpecials;

    private AddressExtractor(MailetContext mailetContext, List<String> allowedSpecials) {
        this.mailetContext = mailetContext;
        this.allowedSpecials = allowedSpecials;
    }

    private List<MailAddress> extract(Optional<String> maybeAddressList) throws MessagingException {
        if (!maybeAddressList.isPresent()) {
            return ImmutableList.of();
        }
        String addressList = maybeAddressList.get();
        try {
            return toMailAddresses(ImmutableList.copyOf(InternetAddress.parse(addressList, ENFORCE_RFC822_SYNTAX)));
        } catch (AddressException e) {
            throw new MessagingException("Exception thrown parsing: " + addressList, e);
        }
    }

    private List<MailAddress> toMailAddresses(List<InternetAddress> addresses) throws MessagingException {
        ImmutableList.Builder<MailAddress> builder = ImmutableList.builder();
        for (InternetAddress address : addresses) {
            builder.add(toMailAddress(address));
        }
        return builder.build();
    }

    private MailAddress toMailAddress(InternetAddress address) throws MessagingException {
        try {
            Optional<MailAddress> specialAddress = getSpecialAddress(address.getAddress());
            if (specialAddress.isPresent()) {
                return specialAddress.get();
            }
            return new MailAddress(address);
        } catch (Exception e) {
            throw new MessagingException("Exception thrown parsing: " + address.getAddress(), e);
        }
    }

    /**
     * Returns an {@link Optional} the {@link SpecialAddress} that corresponds to an init parameter
     * value. The init parameter value is checked against a List<String> of allowed
     * values. The checks are case insensitive.
     *
     * @param addressString   the string to check if is a special address
     * @param allowedSpecials a List<String> with the allowed special addresses
     * @return a SpecialAddress if found, absent if not found or addressString is
     *         null
     * @throws MessagingException if is a special address not in the allowedSpecials list
     */
    private Optional<MailAddress> getSpecialAddress(String addressString) throws MessagingException {
        if (Strings.isNullOrEmpty(addressString)) {
            return Optional.empty();
        }

        Optional<MailAddress> specialAddress = asSpecialAddress(addressString);
        if (specialAddress.isPresent()) {
            if (!isAllowed(addressString, allowedSpecials)) {
                throw new MessagingException("Special (\"magic\") address found not allowed: " + addressString + ", allowed values are \"" + StringUtils.listToString(allowedSpecials) + "\"");
            }
            return specialAddress;
        }
        return Optional.empty();
    }

    private Optional<MailAddress> asSpecialAddress(String addressString) {
        String lowerCaseTrimed = addressString.toLowerCase(Locale.US).trim();
        if (lowerCaseTrimed.equals("postmaster")) {
            return Optional.of(mailetContext.getPostmaster());
        }
        if (lowerCaseTrimed.equals("sender")) {
            return Optional.of(SpecialAddress.SENDER);
        }
        if (lowerCaseTrimed.equals("reversepath")) {
            return Optional.of(SpecialAddress.REVERSE_PATH);
        }
        if (lowerCaseTrimed.equals("from")) {
            return Optional.of(SpecialAddress.FROM);
        }
        if (lowerCaseTrimed.equals("replyto")) {
            return Optional.of(SpecialAddress.REPLY_TO);
        }
        if (lowerCaseTrimed.equals("to")) {
            return Optional.of(SpecialAddress.TO);
        }
        if (lowerCaseTrimed.equals("recipients")) {
            return Optional.of(SpecialAddress.RECIPIENTS);
        }
        if (lowerCaseTrimed.equals("delete")) {
            return Optional.of(SpecialAddress.DELETE);
        }
        if (lowerCaseTrimed.equals("unaltered")) {
            return Optional.of(SpecialAddress.UNALTERED);
        }
        if (lowerCaseTrimed.equals("null")) {
            return Optional.of(SpecialAddress.NULL);
        }
        return Optional.empty();
    }

    private boolean isAllowed(String addressString, List<String> allowedSpecials) {
        return allowedSpecials.stream()
            .anyMatch(allowedSpecial -> addressString.equals(allowedSpecial.toLowerCase(Locale.US).trim()));
    }
}
