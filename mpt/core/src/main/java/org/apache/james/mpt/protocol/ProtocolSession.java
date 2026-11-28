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

package org.apache.james.mpt.protocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.james.mpt.api.ProtocolInteractor;
import org.apache.james.mpt.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

/**
 * A protocol session which can be run against a reader and writer, which checks
 * the server response against the expected values. TODO make ProtocolSession
 * itself be a permissible ProtocolElement, so that we can nest and reuse
 * sessions.
 */
public class ProtocolSession implements ProtocolInteractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolSession.class);

    private boolean continued = false;

    private boolean continuationExpected = false;

    private int maxSessionNumber;

    protected List<ProtocolElement> testElements = new ArrayList<>();

    private Iterator<ProtocolElement> elementsIterator;

    private Session[] sessions;

    private ProtocolElement nextTest;

    private boolean continueAfterFailure = false;

    private Map<String, Stopwatch> timers = new HashMap<>();

    /**
     * Runtime variables. They are substituted (<code>${name}</code>) into client lines (verbatim)
     * and into expected server lines (quoted as regex literals) when the element is executed.
     * They are fed by {@link #setVariable(String, String)}, by <code>SET:</code> directives and
     * by the named groups (<code>(?&lt;name&gt;...)</code>) of matched server lines.
     */
    private final Map<String, String> variables = new ConcurrentHashMap<>();

    private final List<String> failures = new CopyOnWriteArrayList<>();

    /** Last line actually sent (after substitution) per session, prefixed by its script location. */
    private final Map<Integer, String> lastSent = new ConcurrentHashMap<>();

    /** Patterns of server lines to skip, per session. */
    private final Map<Integer, List<String>> ignores = new ConcurrentHashMap<>();

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final int MAX_UNTIL_LINES = 10_000;

    public void setVariable(String name, String value) {
        if (!VARIABLE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid variable name '" + name + "'");
        }
        variables.put(name, value);
    }

    public Map<String, String> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    /**
     * Failures recorded while running with {@link #setContinueAfterFailure(boolean)} set to true.
     */
    public List<String> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    private String substitute(String line, boolean asRegex) {
        if (variables.isEmpty() || !line.contains("${")) {
            return line;
        }
        StringBuilder result = new StringBuilder();
        int position = 0;
        while (position < line.length()) {
            int start = line.indexOf("${", position);
            if (start < 0) {
                break;
            }
            int end = line.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String value = variables.get(line.substring(start + 2, end));
            result.append(line, position, start);
            if (value == null) {
                result.append(line, start, end + 1);
            } else if (asRegex) {
                result.append(Pattern.quote(value));
            } else {
                result.append(value);
            }
            position = end + 1;
        }
        result.append(line.substring(position));
        return result.toString();
    }

    private boolean matchAndCapture(String expected, String actual) {
        Matcher matcher;
        try {
            matcher = Pattern.compile(expected).matcher(actual);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException("Invalid expected pattern (undefined variable?) '" + expected + "': " + e.getDescription(), e);
        }
        if (!matcher.matches()) {
            return false;
        }
        matcher.pattern().namedGroups().keySet().forEach(name -> {
            String value = matcher.group(name);
            if (value != null) {
                variables.put(name, value);
            }
        });
        return true;
    }

    public final boolean isContinueAfterFailure() {
        return continueAfterFailure;
    }

    enum LolLevel {
        Debug,
        Info,
        Warn,
        Err
    }

    public final void setContinueAfterFailure(boolean continueAfterFailure) {
        this.continueAfterFailure = continueAfterFailure;
    }

    /**
     * Returns the number of sessions required to run this ProtocolSession. If
     * the number of readers and writers provided is less than this number, an
     * exception will occur when running the tests.
     */
    public int getSessionCount() {
        return maxSessionNumber + 1;
    }

    /**
     * Executes the ProtocolSession in real time against the readers and writers
     * supplied, writing client requests and reading server responses in the
     * order that they appear in the test elements. The index of a reader/writer
     * in the array corresponds to the number of the session. If an exception
     * occurs, no more test elements are executed.
     */
    public void runSessions(Session[] sessions) throws Exception {
        this.sessions = sessions;
        elementsIterator = testElements.iterator();
        while (elementsIterator.hasNext()) {
            Object obj = elementsIterator.next();
            if (obj instanceof ProtocolElement) {
                ProtocolElement test = (ProtocolElement) obj;
                test.testProtocol(sessions, continueAfterFailure);
            }
        }
    }

    public void doContinue() {
        try {
            if (continuationExpected) {
                continued = true;
                while (elementsIterator.hasNext()) {
                    Object obj = elementsIterator.next();
                    if (obj instanceof ProtocolElement) {
                        nextTest = (ProtocolElement) obj;

                        if (!nextTest.isClient()) {
                            break;
                        }
                        nextTest.testProtocol(sessions, continueAfterFailure);
                    }
                }
                if (!elementsIterator.hasNext()) {
                    nextTest = null;
                }
            } else {
                throw new RuntimeException("Unexpected continuation");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * adds a new Client request line to the test elements
     */
    @Override
    public void cl(String clientLine) {
        testElements.add(new ClientRequest(clientLine));
    }

    /**
     * adds a new Server Response line to the test elements, with the specified
     * location.
     */
    @Override
    public void sl(String serverLine, String location) {
        testElements.add(new ServerResponse(serverLine, location));
    }

    /**
     * adds a new Server Unordered Block to the test elements.
     */
    @Override
    public void sub(List<String> serverLines, String location) {
        testElements.add(new ServerUnorderedBlockResponse(-1, serverLines, location));
    }

    /**
     * adds a new Client request line to the test elements
     */
    @Override
    public void cl(int sessionNumber, String clientLine) {
        cl(sessionNumber, clientLine, null);
    }

    public void cl(int sessionNumber, String clientLine, String location) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new ClientRequest(sessionNumber, clientLine, location));
    }

    /**
     * Adds a continuation. To allow one thread to be used for testing.
     */
    @Override
    public void cont(int sessionNumber) throws Exception {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new ContinuationElement(sessionNumber));
    }

    /**
     * adds a new Server Response line to the test elements, with the specified
     * location.
     */
    @Override
    public void sl(int sessionNumber, String serverLine, String location, String lastClientMessage) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new ServerResponse(sessionNumber, serverLine, location));
    }

    /**
     * adds a new Server Unordered Block to the test elements.
     */
    @Override
    public void sub(int sessionNumber, List<String> serverLines, String location, String lastClientMessage) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new ServerUnorderedBlockResponse(sessionNumber, serverLines, location));
    }

    /**
     * adds a Wait condition
     */
    public void wait(int sessionNumber, long timeToWaitInMs) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new WaitElement(timeToWaitInMs));
    }

    public void log(int sessionNumber, LolLevel level, String message) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new LogElement(level, message));
    }

    public void reinit(int sessionNumber) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new ReinitElement(sessionNumber));
    }

    public void await(int sessionNumber) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new AwaitElement(sessionNumber));
    }

    public void timer(TimerCommand timerCommand, String timerName) {
        testElements.add(new TimerElement(timerCommand, timerName));
    }

    /**
     * Reads server lines, discarding them, until one matches the expected pattern.
     */
    public void until(int sessionNumber, String serverLine, String location, String lastClientMessage) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new UntilResponse(sessionNumber, serverLine, location));
    }

    /**
     * Skips, from now on, server lines matching the pattern when they do not match the expected line.
     * A null pattern clears the ignored patterns.
     */
    public void ignore(int sessionNumber, String pattern) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new IgnoreElement(sessionNumber, pattern));
    }

    /**
     * Sends a command ending with <code>{}</code> or <code>{+}</code>, the literal size being computed from the parts.
     */
    public void literal(int sessionNumber, String command, List<LiteralPart> parts, String location) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new LiteralRequest(sessionNumber, command, parts, location));
    }

    /**
     * Sends the commands and reads the response up to the end pattern, again every interval until one line
     * matches the expected pattern or the timeout elapses.
     */
    public void poll(int sessionNumber, long timeoutMs, long intervalMs, List<String> commands, String expect, String end, String location) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new PollElement(sessionNumber, timeoutMs, intervalMs, commands, expect, end, location));
    }

    /**
     * Upgrades the session connection to TLS (to be used after the server accepted STARTTLS).
     */
    public void startTls(int sessionNumber) {
        this.maxSessionNumber = Math.max(this.maxSessionNumber, sessionNumber);
        testElements.add(new StartTlsElement(sessionNumber));
    }

    /**
     * Defines a runtime variable. The value is itself subject to variable substitution.
     */
    public void set(String name, String value) {
        set(name, value, false);
    }

    /**
     * Defines a runtime variable holding the base64 encoding of the (substituted) UTF-8 value,
     * where <code>\0</code> stands for the NUL character. Handy for SASL PLAIN.
     */
    public void setBase64(String name, String value) {
        set(name, value, true);
    }

    private void set(String name, String value, boolean base64) {
        if (!VARIABLE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid variable name '" + name + "'");
        }
        testElements.add(new SetElement(name, value, base64));
    }

    /**
     * A client request, which write the specified message to a Writer.
     */
    private class ClientRequest implements ProtocolElement {
        private final int sessionNumber;
        private final String message;
        private final String location;

        public ClientRequest(String message) {
            this(-1, message, null);
        }

        public ClientRequest(int sessionNumber, String message, String location) {
            this.sessionNumber = sessionNumber;
            this.message = message;
            this.location = location;
        }

        /**
         * Writes the request message to the PrintWriters. If the sessionNumber
         * == -1, the request is written to *all* supplied writers, otherwise,
         * only the writer for this session is writted to.
         */
        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            for (int index : targetSessions(sessionNumber, sessions)) {
                send(sessions[index], index, substitute(message, false), location);
            }
        }

        @Override
        public boolean isClient() {
            return true;
        }
    }

    private int[] targetSessions(int sessionNumber, Session[] sessions) {
        if (sessionNumber < 0) {
            return java.util.stream.IntStream.range(0, sessions.length).toArray();
        }
        return new int[] {sessionNumber};
    }

    private void send(Session session, int index, String line, String location) throws Exception {
        LOGGER.debug("C: {}", line);
        lastSent.put(index, (location == null ? "" : location + ": ") + line);
        session.writeLine(line);
    }

    private String failure(int index, String location, String details) {
        return "\nLocation: " + location
            + "\nSession: " + index
            + "\nLastClientMsg: " + lastSent.getOrDefault(index, "(nothing sent yet: server greeting)")
            + details;
    }

    private String readServerLine(Session session, int index, String location, String expected) throws Exception {
        try {
            return session.readLine();
        } catch (IOException e) {
            throw new InvalidServerResponseException(failure(index, location, "\nExpected: '" + expected
                + "'\nReason: Server Timeout or connection closed (" + e.getMessage() + ")"));
        }
    }

    private boolean ignored(int index, String line) {
        return ignores.getOrDefault(index, List.of()).stream()
            .anyMatch(pattern -> Pattern.matches(substitute(pattern, true), line));
    }

    /**
     * Represents a single-line server response, which reads a line from a
     * reader, and compares it with the defined regular expression definition of
     * this line. Lines matching an IGNORE pattern (and not the expected one) are skipped.
     */
    private class ServerResponse implements ProtocolElement {
        protected final int sessionNumber;
        protected final String expectedLine;
        protected final String location;

        public ServerResponse(String expectedPattern, String location) {
            this(-1, expectedPattern, location);
        }

        public ServerResponse(int sessionNumber, String expectedPattern, String location) {
            this.sessionNumber = sessionNumber;
            this.expectedLine = expectedPattern;
            this.location = location;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            for (int index : targetSessions(sessionNumber, sessions)) {
                checkResponse(sessions[index], index, continueAfterFailure);
            }
        }

        protected void checkResponse(Session session, int index, boolean continueAfterFailure) throws Exception {
            String expected = substitute(expectedLine, true);
            for (int i = 0; i < MAX_UNTIL_LINES; i++) {
                String testLine = readServerLine(session, index, location, expected);
                LOGGER.debug("S: {}", testLine);
                if (matchAndCapture(expected, testLine)) {
                    return;
                }
                if (!ignored(index, testLine)) {
                    handleFailure(continueAfterFailure, failure(index, location, "\nExpected: '"
                        + expected + "'\nActual   : '" + testLine + "'"));
                    return;
                }
            }
            handleFailure(continueAfterFailure, failure(index, location, "\nExpected: '" + expected
                + "'\nReason: more than " + MAX_UNTIL_LINES + " ignored lines"));
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    /**
     * Represents a set of lines which must be received from the server, in a
     * non-specified order.
     */
    private class ServerUnorderedBlockResponse extends ServerResponse {
        private final List<String> expectedLines;

        public ServerUnorderedBlockResponse(int sessionNumber, List<String> expectedLines, String location) {
            super(sessionNumber, "<Unordered Block>", location);
            this.expectedLines = expectedLines;
        }

        @Override
        protected void checkResponse(Session session, int index, boolean continueAfterFailure) throws Exception {
            List<String> testLines = new ArrayList<>(expectedLines.stream()
                .map(line -> substitute(line, true))
                .toList());
            int skipped = 0;
            while (!testLines.isEmpty()) {
                String actualLine = readServerLine(session, index, location, String.join(" | ", testLines));

                boolean foundMatch = false;
                for (int i = 0; i < testLines.size(); i++) {
                    if (matchAndCapture(testLines.get(i), actualLine)) {
                        foundMatch = true;
                        testLines.remove(i);
                        break;
                    }
                }

                if (!foundMatch && (!ignored(index, actualLine) || ++skipped > MAX_UNTIL_LINES)) {
                    StringBuilder errMsg = new StringBuilder().append("\nExpected one of: ");
                    for (String expectedLine : testLines) {
                        errMsg.append("\n    ");
                        errMsg.append(expectedLine);
                    }
                    errMsg.append("\nActual: ").append(actualLine);
                    handleFailure(continueAfterFailure, failure(index, location, errMsg.toString()));
                    return;
                }
            }
        }
    }

    private class ContinuationElement implements ProtocolElement {

        private final int sessionNumber;

        public ContinuationElement(int sessionNumber) throws Exception {
            this.sessionNumber = Math.max(0, sessionNumber);
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            Session session = sessions[sessionNumber];
            continuationExpected = true;
            continued = false;
            String testLine = session.readLine();
            if (!testLine.startsWith("+") || !continued) {
                final String message = String.format("Expected continuation, got '%s'", testLine);
                handleFailure(continueAfterFailure, message);
            }
            continuationExpected = false;
            continued = false;

            if (nextTest != null) {
                nextTest.testProtocol(sessions, continueAfterFailure);
            }
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    private void handleFailure(boolean continueAfterFailure, String message) throws InvalidServerResponseException {
        if (continueAfterFailure) {
            failures.add(message);
            LOGGER.warn(message);
        } else {
            throw new InvalidServerResponseException(message);
        }
    }

    /**
     * Reads (and discards) server lines until one of them matches the expected pattern.
     */
    private class UntilResponse extends ServerResponse {
        UntilResponse(int sessionNumber, String expectedPattern, String location) {
            super(sessionNumber, expectedPattern, location);
        }

        @Override
        protected void checkResponse(Session session, int index, boolean continueAfterFailure) throws Exception {
            String expected = substitute(expectedLine, true);
            for (int i = 0; i < MAX_UNTIL_LINES; i++) {
                String actualLine = readServerLine(session, index, location, expected);
                LOGGER.debug("S: {}", actualLine);
                if (matchAndCapture(expected, actualLine)) {
                    return;
                }
            }
            handleFailure(continueAfterFailure, failure(index, location, "\nExpected: '" + expected
                + "'\nReason: no matching line within " + MAX_UNTIL_LINES + " lines"));
        }
    }

    /**
     * Adds (or clears, when the pattern is null) the patterns of server lines to skip.
     */
    private class IgnoreElement implements ProtocolElement {
        private final int sessionNumber;
        private final String pattern;

        IgnoreElement(int sessionNumber, String pattern) {
            this.sessionNumber = sessionNumber;
            this.pattern = pattern;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) {
            for (int index : targetSessions(sessionNumber, sessions)) {
                if (pattern == null) {
                    ignores.remove(index);
                } else {
                    ignores.computeIfAbsent(index, any -> new CopyOnWriteArrayList<>()).add(pattern);
                }
            }
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    public record LiteralPart(String text, boolean lineEnding) {
    }

    /**
     * A command ending with <code>{}</code> (synchronizing) or <code>{+}</code> (non synchronizing) followed by
     * literal content whose UTF-8 byte size is computed at runtime.
     */
    private class LiteralRequest implements ProtocolElement {
        private final int sessionNumber;
        private final String command;
        private final List<LiteralPart> parts;
        private final String location;

        LiteralRequest(int sessionNumber, String command, List<LiteralPart> parts, String location) {
            this.sessionNumber = sessionNumber;
            this.command = command;
            this.parts = List.copyOf(parts);
            this.location = location;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            StringBuilder content = new StringBuilder();
            parts.forEach(part -> content.append(substitute(part.text(), false)).append(part.lineEnding() ? "\r\n" : ""));
            int size = content.toString().getBytes(StandardCharsets.UTF_8).length;
            String substituted = substitute(command, false);
            boolean synchronizing = substituted.endsWith("{}");
            String line = substituted.substring(0, substituted.lastIndexOf('{'))
                + (synchronizing ? "{" + size + "}" : "{" + size + "+}");
            for (int index : targetSessions(sessionNumber, sessions)) {
                Session session = sessions[index];
                send(session, index, line, location);
                if (synchronizing && !awaitContinuation(session, index, continueAfterFailure)) {
                    return;
                }
                session.write(content.toString());
            }
        }

        private boolean awaitContinuation(Session session, int index, boolean continueAfterFailure) throws Exception {
            for (int i = 0; i < MAX_UNTIL_LINES; i++) {
                String actualLine = readServerLine(session, index, location, "\\+.*");
                if (actualLine.startsWith("+")) {
                    return true;
                }
                if (!ignored(index, actualLine)) {
                    handleFailure(continueAfterFailure, failure(index, location,
                        "\nExpected: a continuation request ('+ ...') before sending the literal\nActual   : '" + actualLine + "'"));
                    return false;
                }
            }
            return false;
        }

        @Override
        public boolean isClient() {
            return true;
        }
    }

    /**
     * Repeats commands until a response line matches, reading every iteration up to the END line.
     */
    private class PollElement implements ProtocolElement {
        private final int sessionNumber;
        private final long timeoutMs;
        private final long intervalMs;
        private final List<String> commands;
        private final String expect;
        private final String end;
        private final String location;

        PollElement(int sessionNumber, long timeoutMs, long intervalMs, List<String> commands, String expect, String end, String location) {
            this.sessionNumber = sessionNumber;
            this.timeoutMs = timeoutMs;
            this.intervalMs = intervalMs;
            this.commands = List.copyOf(commands);
            this.expect = expect;
            this.end = end;
            this.location = location;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            for (int index : targetSessions(sessionNumber, sessions)) {
                poll(sessions[index], index, continueAfterFailure);
            }
        }

        private void poll(Session session, int index, boolean continueAfterFailure) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMs;
            List<String> lastResponse = List.of();
            for (int attempt = 1; ; attempt++) {
                variables.put("pollAttempt", String.valueOf(attempt));
                for (String command : commands) {
                    send(session, index, substitute(command, false), location);
                }
                String expected = substitute(expect, true);
                String expectedEnd = substitute(end, true);
                boolean found = false;
                boolean ended = false;
                List<String> response = new ArrayList<>();
                for (int i = 0; i < MAX_UNTIL_LINES && !ended; i++) {
                    String line = readServerLine(session, index, location, expectedEnd);
                    response.add(line);
                    if (!found && matchAndCapture(expected, line)) {
                        found = true;
                    }
                    ended = matchAndCapture(expectedEnd, line);
                }
                if (!ended) {
                    handleFailure(continueAfterFailure, failure(index, location, "\nExpected end of response: '" + expectedEnd
                        + "'\nReason: not found within " + MAX_UNTIL_LINES + " lines"));
                    return;
                }
                if (found) {
                    return;
                }
                lastResponse = response;
                if (System.currentTimeMillis() + intervalMs > deadline) {
                    handleFailure(continueAfterFailure, failure(index, location, "\nExpected: '" + expected
                        + "'\nReason: POLL gave up after " + attempt + " attempts (" + timeoutMs + " ms)"
                        + "\nLast response: " + String.join(" | ", lastResponse)));
                    return;
                }
                Thread.sleep(intervalMs);
            }
        }

        @Override
        public boolean isClient() {
            return true;
        }
    }

    private static class StartTlsElement implements ProtocolElement {
        private final int sessionNumber;

        StartTlsElement(int sessionNumber) {
            this.sessionNumber = sessionNumber;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            if (sessionNumber < 0) {
                for (Session session : sessions) {
                    session.startTls();
                }
            } else {
                sessions[sessionNumber].startTls();
            }
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    private class SetElement implements ProtocolElement {
        private final String name;
        private final String value;
        private final boolean base64;

        SetElement(String name, String value, boolean base64) {
            this.name = name;
            this.value = value;
            this.base64 = base64;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) {
            String substituted = substitute(value, false);
            if (base64) {
                byte[] bytes = substituted.replace("\\0", "\u0000").getBytes(StandardCharsets.UTF_8);
                variables.put(name, Base64.getEncoder().encodeToString(bytes));
            } else {
                variables.put(name, substituted);
            }
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    private static class ReinitElement implements ProtocolElement {

        private final int sessionNumber;

        public ReinitElement(int sessionNumber) {
            this.sessionNumber = Math.max(0, sessionNumber);
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            Session session = sessions[sessionNumber];
            session.restart();
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    protected enum TimerCommand {
        START, PRINT, RESET;

        public static TimerCommand from(String value) throws InvalidServerResponseException {
            if (value.equalsIgnoreCase("start")) {
                return START;
            }
            if (value.equalsIgnoreCase("print")) {
                return PRINT;
            }
            if (value.equalsIgnoreCase("reset")) {
                return RESET;
            }
            throw new InvalidServerResponseException("Invalid TIMER command '" + value + "'");
        }
    }

    private class TimerElement implements ProtocolElement {

        private TimerCommand timerCommand;
        private String timerName;

        public TimerElement(TimerCommand timerCommand, String timerName) {
            this.timerCommand = timerCommand;
            this.timerName = timerName;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            switch (timerCommand) {
            case START:
                start();
                break;
            case PRINT:
                print();
                break;
            case RESET:
                reset();
                break;
            default:
                throw new InvalidServerResponseException("Invalid TIMER command '" + timerCommand + "' for timer name: '" + timerName + "'");
            }
        }

        private void start() {
            timers.put(timerName, Stopwatch.createStarted());
        }

        private void print() throws InvalidServerResponseException {
            Stopwatch stopwatch = timers.get(timerName);
            if (stopwatch == null) {
                throw new InvalidServerResponseException("TIMER '" + timerName + "' undefined");
            }
            LOGGER.info("Time spent in '{}': {} ms", timerName, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }

        private void reset() throws InvalidServerResponseException {
            Stopwatch stopwatch = timers.get(timerName);
            if (stopwatch == null) {
                throw new InvalidServerResponseException("TIMER '" + timerName + "' undefined");
            }
            stopwatch.reset();
            stopwatch.start();
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    /**
     * Allow you to wait a given time at a given point of the test script
     */
    private static class WaitElement implements ProtocolElement {

        private final long timeToWaitInMs;

        public WaitElement(long timeToWaitInMs) {
            this.timeToWaitInMs = timeToWaitInMs;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            Thread.sleep(timeToWaitInMs);
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    private static class AwaitElement implements ProtocolElement {
        private final int sessionNumber;

        private AwaitElement(int sessionNumber) {
            this.sessionNumber = Math.max(0, sessionNumber);
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            sessions[sessionNumber].await();
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    /**
     * Allow you to wait a given time at a given point of the test script
     */
    private static class LogElement implements ProtocolElement {

        private final LolLevel level;
        private final String message;

        public LogElement(LolLevel level, String message) {
            this.level = level;
            this.message = message;
        }

        @Override
        public void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception {
            switch (level) {
            case Debug:
                LOGGER.debug(message);
                break;
            case Info:
                LOGGER.info(message);
                break;
            case Warn:
                LOGGER.warn(message);
                break;
            case Err:
                LOGGER.error(message);
                break;
            }
        }

        @Override
        public boolean isClient() {
            return false;
        }
    }

    /**
     * Represents a generic protocol element, which may write requests to the
     * server, read responses from the server, or both. Implementations should
     * test the server response against an expected response, and throw an
     * exception on mismatch.
     */
    private interface ProtocolElement {
        /**
         * Executes the ProtocolElement against the supplied session.
         * 
         * @param continueAfterFailure
         *            TODO
         * @throws Exception
         */
        void testProtocol(Session[] sessions, boolean continueAfterFailure) throws Exception;

        boolean isClient();
    }

    /**
     * An exception which is thrown when the actual response from a server is
     * different from that expected.
     */
    public static class InvalidServerResponseException extends Exception {
        public InvalidServerResponseException(String message) {
            super(message);
        }
    }

    /**
     * Constructs a <code>String</code> with all attributes in name = value
     * format.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String TAB = " ";

        String result = "ProtocolSession ( " + "continued = " + this.continued + TAB + "continuationExpected = "
                + this.continuationExpected + TAB + "maxSessionNumber = " + this.maxSessionNumber + TAB
                + "testElements = " + this.testElements + TAB + "elementsIterator = " + this.elementsIterator + TAB
                + "sessions = " + Arrays.toString(this.sessions) + TAB + "nextTest = " + this.nextTest + TAB + "continueAfterFailure = "
                + this.continueAfterFailure + TAB + " )";

        return result;
    }

}
