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

package org.apache.james.mpt.mcp;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread safe, bounded record of the lines exchanged during a script run.
 */
public class Transcript {
    public enum Direction {
        CLIENT("C"), SERVER("S"), EVENT("*");

        private final String tag;

        Direction(String tag) {
            this.tag = tag;
        }
    }

    public record Entry(long elapsedMs, int session, Direction direction, String line) {
        public String format() {
            return String.format("%6dms [%d] %s: %s", elapsedMs, session, direction.tag, line);
        }
    }

    private static final int MAX_LINE_LENGTH = 1000;

    private final long start = System.nanoTime();
    private final int maxLines;
    private final List<Entry> entries = new ArrayList<>();
    private int dropped = 0;

    public Transcript(int maxLines) {
        this.maxLines = maxLines;
    }

    public synchronized void record(int session, Direction direction, String line) {
        if (entries.size() >= maxLines) {
            dropped++;
            return;
        }
        String truncated = line.length() > MAX_LINE_LENGTH
            ? line.substring(0, MAX_LINE_LENGTH) + "...(" + line.length() + " chars)"
            : line;
        entries.add(new Entry((System.nanoTime() - start) / 1_000_000, session, direction, truncated));
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int dropped() {
        return dropped;
    }

    public synchronized String format() {
        StringBuilder builder = new StringBuilder();
        entries.forEach(entry -> builder.append(entry.format()).append('\n'));
        if (dropped > 0) {
            builder.append("... ").append(dropped).append(" more lines not recorded\n");
        }
        return builder.toString();
    }
}
