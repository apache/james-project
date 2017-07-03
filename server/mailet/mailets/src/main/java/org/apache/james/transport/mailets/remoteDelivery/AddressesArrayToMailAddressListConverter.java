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

package org.apache.james.transport.mailets.remoteDelivery;

import java.util.Arrays;
import java.util.List;

import javax.mail.Address;
import javax.mail.internet.AddressException;

import org.apache.mailet.MailAddress;
import org.slf4j.Logger;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

public class AddressesArrayToMailAddressListConverter {

    public static List<MailAddress> getAddressesAsMailAddress(Address[] addresses, final Logger logger) {
        if (addresses == null) {
            return ImmutableList.of();
        }
        return FluentIterable.from(Arrays.asList(addresses)).transform(new Function<Address, Optional<MailAddress>>() {
            @Override
            public Optional<MailAddress> apply(Address input) {
                try {
                    return Optional.of(new MailAddress(input.toString()));
                } catch (AddressException e) {
                    logger.debug("Can't parse unsent address " + input, e);
                    return Optional.absent();
                }
            }
        }).filter(new Predicate<Optional<MailAddress>>() {
            @Override
            public boolean apply(Optional<MailAddress> input) {
                return input.isPresent();
            }
        }).transform(new Function<Optional<MailAddress>, MailAddress>() {
            @Override
            public MailAddress apply(Optional<MailAddress> input) {
                return input.get();
            }
        }).toList();
    }

}
