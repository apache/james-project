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

package org.apache.james.transport.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.jakewharton.byteunits.BinaryByteUnit;

public class SizeUtils {

    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.US));

    public static String humanReadableSize(Number size) {
        return humanReadableSize(size.longValue());
    }

    private static String humanReadableSize(long size) {
        if (size < 0) {
            return "-" + humanReadableSize(-size);
        } else {
            return BinaryByteUnit.format(size, DECIMAL_FORMAT);
        }
    }
}
