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

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.commons.compress.archivers.zip.ZipShort;
import org.apache.james.util.StreamUtils;

public class FlagsExtraField extends StringExtraField implements WithZipHeader {

    public static final ZipShort ID_AP = new ZipShort(WithZipHeader.toLittleEndian('a', 'p'));

    private static String serializeFlags(Flags flags) {
        return Stream.concat(
                StreamUtils.ofNullable(flags.getSystemFlags())
                    .map(FlagsExtraField::systemFlagToString),
                StreamUtils.ofNullable(flags.getUserFlags()))
            .collect(Collectors.joining("%"));
    }

    public FlagsExtraField() {
        super();
    }

    public FlagsExtraField(Flags flags) {
        super(Optional.of(serializeFlags(flags)));
    }

    @Override
    public ZipShort getHeaderId() {
        return ID_AP;
    }

    private static String systemFlagToString(Flags.Flag flag) throws RuntimeException {
        if (flag == Flags.Flag.ANSWERED) {
            return "\\ANSWERED";
        } else if (flag == Flags.Flag.DELETED) {
            return "\\DELETED";
        } else if (flag == Flags.Flag.DRAFT) {
            return "\\DRAFT";
        } else if (flag == Flags.Flag.FLAGGED) {
            return "\\FLAGGED";
        } else if (flag == Flags.Flag.RECENT) {
            return "\\RECENT";
        } else if (flag == Flags.Flag.SEEN) {
            return "\\SEEN";
        }
        throw new RuntimeException("Unknown system flag");
    }
}
