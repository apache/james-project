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

package org.apache.james.mailbox.backup.zip;

import java.util.Date;
import java.util.Optional;

import org.apache.commons.compress.archivers.zip.ZipShort;

public class InternalDateExtraField extends LongExtraField implements WithZipHeader {

    public static final ZipShort ID_AO = new ZipShort(WithZipHeader.toLittleEndian('a', 'o'));

    public InternalDateExtraField() {
        super();
    }

    public InternalDateExtraField(Optional<Date> date) {
        super(date
            .map(Date::getTime));
    }

    public InternalDateExtraField(Date date) {
        this(Optional.of(date));
    }

    public InternalDateExtraField(long timestamp) {
        this(Optional.of(new Date(timestamp)));
    }

    @Override
    public ZipShort getHeaderId() {
        return ID_AO;
    }

    public Optional<Date> getDateValue() {
        return getValue().map(Date::new);
    }
}
