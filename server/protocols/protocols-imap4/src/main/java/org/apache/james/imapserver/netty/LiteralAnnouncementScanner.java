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

package org.apache.james.imapserver.netty;

import java.util.Optional;

import io.netty.buffer.ByteBuf;

/**
 * Spots a non synchronizing literal ({@code {n+}}, or {@code ~{n+}} for a binary one) announced at the end of a
 * line, whatever the number of bytes of the line and however it is split into segments: the line is fed to it
 * from its first byte, with no bound on the size announcement (eg leading zeros).
 */
class LiteralAnnouncementScanner {
    private static final long TOO_BIG = Integer.MAX_VALUE + 1L;

    private enum State {
        NONE,
        OPENING_BRACE,
        DIGITS,
        PLUS,
        CLOSING_BRACE,
        CR,
        ANNOUNCED
    }

    private State state = State.NONE;
    private long size = 0;

    void scan(ByteBuf buffer, int from, int to) {
        for (int i = from; i < to; i++) {
            next(buffer.getByte(i));
        }
    }

    private void next(byte b) {
        if (b == '{') {
            state = State.OPENING_BRACE;
            size = 0;
        } else if (b >= '0' && b <= '9' && (state == State.OPENING_BRACE || state == State.DIGITS)) {
            state = State.DIGITS;
            size = Math.min(size * 10 + (b - '0'), TOO_BIG);
        } else if (b == '+' && state == State.DIGITS) {
            state = State.PLUS;
        } else if (b == '}' && state == State.PLUS) {
            state = State.CLOSING_BRACE;
        } else if (b == '\r' && state == State.CLOSING_BRACE) {
            state = State.CR;
        } else if (b == '\n' && (state == State.CLOSING_BRACE || state == State.CR)) {
            state = State.ANNOUNCED;
        } else {
            state = State.NONE;
        }
    }

    /**
     * @return the size of the literal announced at the end of the line scanned, if any. A size beyond the int
     * range is reported as {@code Integer.MAX_VALUE + 1}.
     */
    Optional<Long> announcedLiteralSize() {
        if (state == State.ANNOUNCED) {
            return Optional.of(size);
        }
        return Optional.empty();
    }

    void reset() {
        state = State.NONE;
        size = 0;
    }
}
