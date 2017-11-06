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


package org.apache.mailet.base;

import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.lang3.time.FastDateFormat;

public class DateFormats {

    public static FastDateFormat getRFC822FormatForTimeZone(TimeZone timeZone) {
        return FastDateFormat.getInstance("EEE, d MMM yyyy HH:mm:ss 'XXXXX' (z)", timeZone, Locale.US);
    }

    public static FastDateFormat RFC822_DATE_FORMAT = FastDateFormat.getInstance("EEE, d MMM yyyy HH:mm:ss 'XXXXX' (z)", Locale.US);
    public static FastDateFormat RFC977_SHORT_DATE_FORMAT = FastDateFormat.getInstance("yyMMdd HHmmss", Locale.US);
    public static FastDateFormat RFC977_LONG_DATE_FORMAT = FastDateFormat.getInstance("yyyyMMdd HHmmss", Locale.US);
    public static FastDateFormat RFC2980_LONG_DATE_FORMAT = FastDateFormat.getInstance("yyyyMMddHHmmss", Locale.US);
}

