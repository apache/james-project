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

package org.apache.james.mpt.helper;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

public class ScriptBuilder {

    public static ScriptBuilder open(String host, int port) throws Exception {
        InetSocketAddress address = new InetSocketAddress(host, port);
        SocketChannel socket = SocketChannel.open(address);
        socket.configureBlocking(false);
        Client client = new Client(socket, socket);
        return new ScriptBuilder(client);
    }

    private int tagCount = 0;

    private boolean uidSearch = false;

    private boolean peek = false;

    private int messageNumber = 1;

    private String user = "imapuser";

    private String password = "password";

    private String mailbox = "testmailbox";

    private String file = "rfc822.mail";

    private String basedir = "/org/apache/james/imap/samples/";

    private boolean createdMailbox = false;

    private final Client client;

    private Fetch fetch = new Fetch();

    private Search search = new Search();

    private String partialFetch = "";

    public ScriptBuilder(Client client) {
        super();
        this.client = client;
    }

    public final boolean isPeek() {
        return peek;
    }

    public final void setPeek(boolean peek) {
        this.peek = peek;
    }

    public final boolean isUidSearch() {
        return uidSearch;
    }

    public final void setUidSearch(boolean uidSearch) {
        this.uidSearch = uidSearch;
    }

    public final String getBasedir() {
        return basedir;
    }

    public final void setBasedir(String basedir) {
        this.basedir = basedir;
    }

    public final String getFile() {
        return file;
    }

    public final void setFile(String file) {
        this.file = file;
    }

    private InputStream openFile() throws Exception {
        InputStream result = this.getClass()
                .getResourceAsStream(basedir + file);
        return new IgnoreHeaderInputStream(result);
    }

    public final Fetch getFetch() {
        return fetch;
    }

    public final void setFetch(Fetch fetch) {
        this.fetch = fetch;
    }

    public final Fetch resetFetch() {
        this.fetch = new Fetch();
        return fetch;
    }

    public final int getMessageNumber() {
        return messageNumber;
    }

    public final void setMessageNumber(int messageNumber) {
        this.messageNumber = messageNumber;
    }

    public final String getMailbox() {
        return mailbox;
    }

    public final ScriptBuilder setMailbox(String mailbox) {
        this.mailbox = mailbox;
        return this;
    }

    public final String getPassword() {
        return password;
    }

    public final void setPassword(String password) {
        this.password = password;
    }

    public final String getUser() {
        return user;
    }

    public final void setUser(String user) {
        this.user = user;
    }

    public void login() throws Exception {
        command("LOGIN " + user + " " + password);
    }

    private void command(String command) throws Exception {
        tag();
        write(command);
        lineEnd();
        response();
    }

    public ScriptBuilder rename(String to) throws Exception {
        return rename(getMailbox(), to);
    }

    public ScriptBuilder rename(String from, String to) throws Exception {
        command("RENAME " + from + " " + to);
        return this;
    }

    public ScriptBuilder select() throws Exception {
        command("SELECT " + mailbox);
        return this;
    }

    public ScriptBuilder create() throws Exception {
        command("CREATE " + mailbox);
        createdMailbox = true;
        return this;
    }

    public ScriptBuilder flagDeleted() throws Exception {
        return flagDeleted(messageNumber);
    }
    
    public ScriptBuilder flagDeleted(int messageNumber) throws Exception {
        store(new Flags().deleted().msn(messageNumber));
        return this;
    }

    public ScriptBuilder expunge() throws Exception {
        command("EXPUNGE");
        return this;
    }

    public void delete() throws Exception {
        if (createdMailbox) {
            command("DELETE " + mailbox);
        }
    }

    public void search() throws Exception {
        search.setUidSearch(uidSearch);
        command(search.command());
        search = new Search();
    }

    public ScriptBuilder all() {
        search.all();
        return this;
    }

    public ScriptBuilder answered() {
        search.answered();
        return this;
    }

    public ScriptBuilder bcc(String address) {
        search.bcc(address);
        return this;
    }

    public ScriptBuilder before(int year, int month, int day) {
        search.before(year, month, day);
        return this;
    }

    public ScriptBuilder body(String text) {
        search.body(text);
        return this;
    }

    public ScriptBuilder cc(String address) {
        search.cc(address);
        return this;
    }

    public ScriptBuilder deleted() {
        search.deleted();
        return this;
    }

    public ScriptBuilder draft() {
        search.draft();
        return this;
    }

    public ScriptBuilder flagged() {
        search.flagged();
        return this;
    }

    public ScriptBuilder from(String address) {
        search.from(address);
        return this;
    }

    public ScriptBuilder header(String field, String value) {
        search.header(field, value);
        return this;
    }

    public ScriptBuilder keyword(String flag) {
        search.keyword(flag);
        return this;
    }

    public ScriptBuilder larger(long size) {
        search.larger(size);
        return this;
    }

    public ScriptBuilder newOperator() {
        search.newOperator();
        return this;
    }

    public ScriptBuilder not() {
        search.not();
        return this;
    }

    public ScriptBuilder old() {
        search.old();
        return this;
    }

    public ScriptBuilder on(int year, int month, int day) {
        search.on(year, month, day);
        return this;
    }

    public ScriptBuilder or() {
        search.or();
        return this;
    }

    public ScriptBuilder recent() {
        search.recent();
        return this;
    }

    public ScriptBuilder seen() {
        search.seen();
        return this;
    }

    public ScriptBuilder sentbefore(int year, int month, int day) {
        search.sentbefore(year, month, day);
        return this;
    }

    public ScriptBuilder senton(int year, int month, int day) {
        search.senton(year, month, day);
        return this;
    }

    public ScriptBuilder sentsince(int year, int month, int day) {
        search.sentsince(year, month, day);
        return this;
    }

    public ScriptBuilder since(int year, int month, int day) {
        search.since(year, month, day);
        return this;
    }

    public ScriptBuilder smaller(int size) {
        search.smaller(size);
        return this;
    }

    public ScriptBuilder subject(String address) {
        search.subject(address);
        return this;
    }

    public ScriptBuilder text(String text) {
        search.text(text);
        return this;
    }

    public ScriptBuilder to(String address) {
        search.to(address);
        return this;
    }

    public ScriptBuilder uid() {
        search.uid();
        return this;
    }

    public ScriptBuilder unanswered() {
        search.unanswered();
        return this;
    }

    public ScriptBuilder undeleted() {
        search.undeleted();
        return this;
    }

    public ScriptBuilder undraft() {
        search.undraft();
        return this;
    }

    public ScriptBuilder unflagged() {
        search.unflagged();
        return this;
    }

    public ScriptBuilder unkeyword(String flag) {
        search.unkeyword(flag);
        return this;
    }

    public ScriptBuilder unseen() {
        search.unseen();
        return this;
    }

    public ScriptBuilder openParen() {
        search.openParen();
        return this;
    }

    public ScriptBuilder closeParen() {
        search.closeParen();
        return this;
    }

    public ScriptBuilder msn(int low, int high) {
        search.msn(low, high);
        return this;
    }

    public ScriptBuilder msnAndUp(int limit) {
        search.msnAndUp(limit);
        return this;
    }

    public ScriptBuilder msnAndDown(int limit) {
        search.msnAndDown(limit);
        return this;
    }

    public Flags flags() {
        return new Flags();
    }

    public void store(Flags flags) throws Exception {
        String command = flags.command();
        command(command);
    }

    public Search getSearch() throws Exception {
        return search;
    }

    public ScriptBuilder partial(long start, long octets) {
        partialFetch = "<" + start + "." + octets + ">";
        return this;
    }

    public ScriptBuilder fetchSection(String section) throws Exception {
        StringBuilder command = new StringBuilder("FETCH ");
        command.append(messageNumber);
        if (peek) {
            command.append(" (BODY.PEEK[");
        } else {
            command.append(" (BODY[");
        }
        command.append(section).append("]").append(partialFetch).append(")");
        command(command.toString());
        return this;
    }

    public void fetchAllMessages() throws Exception {
        final String command = fetch.command();
        command(command);
    }

    public ScriptBuilder list() throws Exception {
        command("LIST \"\" \"*\"");
        return this;
    }

    public void fetchBody() throws Exception {

    }

    public void fetch() throws Exception {
        final String command = fetch.command(messageNumber);
        command(command);
    }

    public void fetchFlags() throws Exception {
        final String command = "FETCH " + messageNumber + " (FLAGS)";
        command(command);
    }

    public void append() throws Exception {
        tag();
        write("APPEND " + mailbox);
        write(openFile());
        lineEnd();
        response();
    }

    private void write(InputStream in) throws Exception {
        client.write(in);
    }

    private void response() throws Exception {
        client.readResponse();
    }

    private void tag() throws Exception {
        client.lineStart();
        write("A" + ++tagCount + " ");
    }

    private void lineEnd() throws Exception {
        client.lineEnd();
    }

    private void write(String phrase) throws Exception {
        client.write(phrase);
    }

    public void close() throws Exception {
        client.close();
    }

    public void logout() throws Exception {
        command("LOGOUT");
    }

    public void quit() throws Exception {
        delete();
        logout();
        close();
    }

    public static final class Flags {
        private final StringBuffer flags;

        private final StringBuffer msn;

        private boolean first;

        private boolean silent;

        private boolean add;

        private boolean subtract;

        public Flags() {
            add = false;
            subtract = false;
            silent = false;
            first = true;
            flags = new StringBuffer("(");
            msn = new StringBuffer();
        }

        public Flags msn(long number) {
            msn.append(number);
            msn.append(' ');
            return this;
        }

        public Flags range(long low, long high) {
            msn.append(low);
            msn.append(':');
            msn.append(high);
            msn.append(' ');
            return this;
        }

        public Flags rangeTill(long number) {
            msn.append("*:");
            msn.append(number);
            msn.append(' ');
            return this;
        }

        public Flags rangeFrom(long number) {
            msn.append(number);
            msn.append(":* ");
            return this;
        }

        public Flags add() {
            add = true;
            subtract = false;
            return this;
        }

        public Flags subtract() {
            add = false;
            subtract = true;
            return this;
        }

        public Flags silent() {
            silent = true;
            return this;
        }

        public Flags deleted() {
            return append("\\DELETED");
        }

        public Flags flagged() {
            return append("\\FLAGGED");
        }

        public Flags answered() {
            return append("\\ANSWERED");
        }

        public Flags seen() {
            return append("\\SEEN");
        }

        public Flags draft() {
            return append("\\DRAFT");
        }

        public String command() {
            String flags;
            if (add) {
                flags = " +FLAGS ";
            } else if (subtract) {
                flags = " -FLAGS ";
            } else {
                flags = " FLAGS ";
            }
            if (silent) {
                flags = flags + ".SILENT";
            }
            return "STORE " + msn + flags + this.flags + ")";
        }

        private Flags append(String term) {
            if (first) {
                first = false;
            } else {
                flags.append(' ');
            }
            flags.append(term);
            return this;
        }
    }

    public static final class Search {

        private StringBuffer buffer;

        private boolean first;

        private boolean uidSearch = false;

        public Search() {
            clear();
        }

        public boolean isUidSearch() {
            return uidSearch;
        }

        public void setUidSearch(boolean uidSearch) {
            this.uidSearch = uidSearch;
        }

        public String command() {
            if (uidSearch) {
                return buffer.insert(0, "UID SEARCH ").toString();
            } else {
                return buffer.insert(0, "SEARCH ").toString();
            }
        }

        public void clear() {
            buffer = new StringBuffer();
            first = true;
        }

        private Search append(long term) {
            return append(Long.valueOf(term).toString());
        }

        private Search append(String term) {
            if (first) {
                first = false;
            } else {
                buffer.append(' ');
            }
            buffer.append(term);
            return this;
        }

        private Search date(int year, int month, int day) {
            append(day);
            switch (month) {
                case 1:
                    buffer.append("-Jan-");
                    break;
                case 2:
                    buffer.append("-Feb-");
                    break;
                case 3:
                    buffer.append("-Mar-");
                    break;
                case 4:
                    buffer.append("-Apr-");
                    break;
                case 5:
                    buffer.append("-May-");
                    break;
                case 6:
                    buffer.append("-Jun-");
                    break;
                case 7:
                    buffer.append("-Jul-");
                    break;
                case 8:
                    buffer.append("-Aug-");
                    break;
                case 9:
                    buffer.append("-Sep-");
                    break;
                case 10:
                    buffer.append("-Oct-");
                    break;
                case 11:
                    buffer.append("-Nov-");
                    break;
                case 12:
                    buffer.append("-Dec-");
                    break;
            }
            buffer.append(year);
            return this;
        }

        public Search all() {
            return append("ALL");
        }

        public Search answered() {
            return append("ANSWERED");
        }

        public Search bcc(String address) {
            return append("BCC " + address);
        }

        public Search before(int year, int month, int day) {
            return append("BEFORE").date(year, month, day);
        }

        public Search body(String text) {
            return append("BODY").append(text);
        }

        public Search cc(String address) {
            return append("CC").append(address);
        }

        public Search deleted() {
            return append("DELETED");
        }

        public Search draft() {
            return append("DRAFT");
        }

        public Search flagged() {
            return append("FLAGGED");
        }

        public Search from(String address) {
            return append("FROM").append(address);
        }

        public Search header(String field, String value) {
            return append("HEADER").append(field).append(value);
        }

        public Search keyword(String flag) {
            return append("KEYWORD").append(flag);
        }

        public Search larger(long size) {
            return append("LARGER").append(size);
        }

        public Search newOperator() {
            return append("NEW");
        }

        public Search not() {
            return append("NOT");
        }

        public Search old() {
            return append("OLD");
        }

        public Search on(int year, int month, int day) {
            return append("ON").date(year, month, day);
        }

        public Search or() {
            return append("OR");
        }

        public Search recent() {
            return append("RECENT");
        }

        public Search seen() {
            return append("SEEN");
        }

        public Search sentbefore(int year, int month, int day) {
            return append("SENTBEFORE").date(year, month, day);
        }

        public Search senton(int year, int month, int day) {
            return append("SENTON").date(year, month, day);
        }

        public Search sentsince(int year, int month, int day) {
            return append("SENTSINCE").date(year, month, day);
        }

        public Search since(int year, int month, int day) {
            return append("SINCE").date(year, month, day);
        }

        public Search smaller(int size) {
            return append("SMALLER").append(size);
        }

        public Search subject(String address) {
            return append("SUBJECT").append(address);
        }

        public Search text(String text) {
            return append("TEXT").append(text);
        }

        public Search to(String address) {
            return append("TO").append(address);
        }

        public Search uid() {
            return append("UID");
        }

        public Search unanswered() {
            return append("UNANSWERED");
        }

        public Search undeleted() {
            return append("UNDELETED");
        }

        public Search undraft() {
            return append("UNDRAFT");
        }

        public Search unflagged() {
            return append("UNFLAGGED");
        }

        public Search unkeyword(String flag) {
            return append("UNKEYWORD").append(flag);
        }

        public Search unseen() {
            return append("UNSEEN");
        }

        public Search openParen() {
            return append("(");
        }

        public Search closeParen() {
            return append(")");
        }

        public Search msn(int low, int high) {
            return append(low + ":" + high);
        }

        public Search msnAndUp(int limit) {
            return append(limit + ":*");
        }

        public Search msnAndDown(int limit) {
            return append("*:" + limit);
        }
    }

    public static final class Fetch {

        static final String[] COMPREHENSIVE_HEADERS = { "DATE", "FROM",
                "TO", "CC", "SUBJECT", "REFERENCES", "IN-REPLY-TO",
                "MESSAGE-ID", "MIME-VERSION", "CONTENT-TYPE", "X-MAILING-LIST",
                "X-LOOP", "LIST-ID", "LIST-POST", "MAILING-LIST", "ORIGINATOR",
                "X-LIST", "SENDER", "RETURN-PATH", "X-BEENTHERE" };

        static final String[] SELECT_HEADERS = { "DATE", "FROM", "TO",
                "ORIGINATOR", "X-LIST" };

        private boolean flagsFetch = false;

        private boolean rfc822Size = false;

        private boolean rfc = false;

        private boolean rfcText = false;

        private boolean rfcHeaders = false;

        private boolean internalDate = false;

        private boolean uid = false;

        private String body = null;

        private boolean bodyFetch = false;

        private boolean bodyStructureFetch = false;

        public boolean isBodyFetch() {
            return bodyFetch;
        }

        public Fetch setBodyFetch(boolean bodyFetch) {
            this.bodyFetch = bodyFetch;
            return this;
        }

        public boolean isBodyStructureFetch() {
            return bodyStructureFetch;
        }

        public Fetch setBodyStructureFetch(boolean bodyStructureFetch) {
            this.bodyStructureFetch = bodyStructureFetch;
            return this;
        }

        public String command(int messageNumber) {
            return "FETCH " + messageNumber + "(" + fetchData() + ")";
        }

        public String command() {
            return "FETCH 1:* (" + fetchData() + ")";
        }

        public boolean isFlagsFetch() {
            return flagsFetch;
        }

        public Fetch setFlagsFetch(boolean flagsFetch) {
            this.flagsFetch = flagsFetch;
            return this;
        }

        public boolean isUid() {
            return uid;
        }

        public Fetch setUid(boolean uid) {
            this.uid = uid;
            return this;
        }

        public boolean isRfc822Size() {
            return rfc822Size;
        }

        public Fetch setRfc822Size(boolean rfc822Size) {
            this.rfc822Size = rfc822Size;
            return this;
        }

        public boolean isRfc() {
            return rfc;
        }

        public Fetch setRfc(boolean rfc) {
            this.rfc = rfc;
            return this;
        }

        public boolean isRfcHeaders() {
            return rfcHeaders;
        }

        public Fetch setRfcHeaders(boolean rfcHeaders) {
            this.rfcHeaders = rfcHeaders;
            return this;
        }

        public boolean isRfcText() {
            return rfcText;
        }

        public Fetch setRfcText(boolean rfcText) {
            this.rfcText = rfcText;
            return this;
        }

        public boolean isInternalDate() {
            return internalDate;
        }

        public Fetch setInternalDate(boolean internalDate) {
            this.internalDate = internalDate;
            return this;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String bodyPeek) {
            this.body = bodyPeek;
        }

        public void bodyPeekCompleteMessage() {
            setBody(buildBody(true, ""));
        }

        public void bodyPeekNotHeaders(String[] fields) {
            setBody(buildBody(true, buildHeaderFields(fields, true)));
        }

        public Fetch bodyPeekHeaders(String[] fields) {
            setBody(buildBody(true, buildHeaderFields(fields, false)));
            return this;
        }

        public String buildBody(boolean peek, String section) {
            StringBuffer result;
            if (peek) {
                result = new StringBuffer("BODY.PEEK[");
            } else {
                result = new StringBuffer("BODY[");
            }
            result.append(section).append("]");
            return result.toString();
        }

        public String buildHeaderFields(String[] fields, boolean not) {
            StringBuffer result;
            if (not) {
                result = new StringBuffer("HEADER.FIELDS.NOT (");
            } else {
                result = new StringBuffer("HEADER.FIELDS (");
            }
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) {
                    result.append(" ");
                }
                result.append(fields[i]);
            }
            result.append(")");
            return result.toString();
        }

        public String fetchData() {
            final StringBuffer buffer = new StringBuffer();
            boolean first = true;
            if (flagsFetch) {
                first = add(buffer, first, "FLAGS");
            }
            if (rfc822Size) {
                first = add(buffer, first, "RFC822.SIZE");
            }
            if (rfc) {
                first = add(buffer, first, "RFC822");
            }
            if (rfcHeaders) {
                first = add(buffer, first, "RFC822.HEADER");
            }
            if (rfcText) {
                first = add(buffer, first, "RFC822.TEXT");
            }
            if (internalDate) {
                first = add(buffer, first, "INTERNALDATE");
            }
            if (uid) {
                first = add(buffer, first, "UID");
            }
            if (bodyFetch) {
                first = add(buffer, first, "BODY");
            }
            if (bodyStructureFetch) {
                first = add(buffer, first, "BODYSTRUCTURE");
            }
            add(buffer, first, body);
            return buffer.toString();
        }

        private boolean add(StringBuffer buffer, boolean first,
                String atom) {
            if (atom != null) {
                if (first) {
                    first = false;
                } else {
                    buffer.append(" ");
                }
                buffer.append(atom);
            }
            return first;
        }
    }

    public static final class Client {

        private final Out out;

        private final ReadableByteChannel source;

        private final WritableByteChannel sump;

        private final ByteBuffer inBuffer = ByteBuffer.allocate(256);

        private final ByteBuffer outBuffer = ByteBuffer.allocate(262144);

        private final ByteBuffer crlf;

        private boolean isLineTagged = false;

        private int continuationBytes = 0;

        public Client(ReadableByteChannel source, WritableByteChannel sump)
                throws Exception {
            super();
            this.source = source;
            this.sump = sump;
            this.out = new Out();
            byte[] crlf = { '\r', '\n' };
            this.crlf = ByteBuffer.wrap(crlf);
            inBuffer.flip();
            readLine();
        }

        public void write(InputStream in) throws Exception {
            outBuffer.clear();
            int next = in.read();
            while (next != -1) {
                if (next == '\n') {
                    outBufferNext((byte) '\r');
                    outBufferNext((byte) '\n');
                } else if (next == '\r') {
                    outBufferNext((byte) '\r');
                    outBufferNext((byte) '\n');
                    next = in.read();
                    if (next == '\n') {
                        next = in.read();
                    } else if (next != -1) {
                        outBufferNext((byte) next);
                    }
                } else {
                    outBufferNext((byte) next);
                }
                next = in.read();
            }

            writeOutBuffer();
        }

        public void outBufferNext(byte next) throws Exception {
            outBuffer.put(next);
        }

        private void writeOutBuffer() throws Exception {
            outBuffer.flip();
            int count = outBuffer.limit();
            String continuation = " {" + count + "+}";
            write(continuation);
            lineEnd();
            out.client();
            while (outBuffer.hasRemaining()) {
                final byte next = outBuffer.get();
                print(next);
                if (next == '\n') {
                    out.client();
                }
            }
            outBuffer.rewind();
            while (outBuffer.hasRemaining()) {
                sump.write(outBuffer);
            }
        }

        public void readResponse() throws Exception {
            isLineTagged = false;
            while (!isLineTagged) {
                readLine();
            }
        }

        private byte next() throws Exception {
            byte result;
            if (inBuffer.hasRemaining()) {
                result = inBuffer.get();
                print(result);
            } else {
                inBuffer.compact();
                int i = 0;
                while ((i = source.read(inBuffer)) == 0) {
                    ;
                }
                if (i == -1) {
                    throw new RuntimeException("Unexpected EOF");
                }
                inBuffer.flip();
                result = next();
            }
            return result;
        }

        private void print(char next) {
            out.print(next);
        }

        private void print(byte next) {
            print((char) next);
        }

        public void lineStart() throws Exception {
            out.client();
        }

        public void write(String phrase) throws Exception {
            out.print(phrase);
            final ByteBuffer buffer = US_ASCII.encode(phrase);
            writeRemaining(buffer);
        }

        public void writeLine(String line) throws Exception {
            lineStart();
            write(line);
            lineEnd();
        }

        private void writeRemaining(ByteBuffer buffer) throws IOException {
            while (buffer.hasRemaining()) {
                sump.write(buffer);
            }
        }

        public void lineEnd() throws Exception {
            out.lineEnd();
            crlf.rewind();
            writeRemaining(crlf);
        }

        private void readLine() throws Exception {
            out.server();

            final byte next = next();
            isLineTagged = next != '*';
            readRestOfLine(next);
        }

        private void readRestOfLine(byte next) throws Exception {
            while (next != '\r') {
                if (next == '{') {
                    startContinuation();
                }
                next = next();
            }
            next();
        }

        private void startContinuation() throws Exception {
            continuationBytes = 0;
            continuation();
        }

        private void continuation() throws Exception {
            byte next = next();
            switch (next) {
                case '0':
                    continuationDigit(0);
                    break;
                case '1':
                    continuationDigit(1);
                    break;
                case '2':
                    continuationDigit(2);
                    break;
                case '3':
                    continuationDigit(3);
                    break;
                case '4':
                    continuationDigit(4);
                    break;
                case '5':
                    continuationDigit(5);
                    break;
                case '6':
                    continuationDigit(6);
                    break;
                case '7':
                    continuationDigit(7);
                    break;
                case '8':
                    continuationDigit(8);
                    break;
                case '9':
                    continuationDigit(9);
                    break;
                case '+':
                    next();
                    next();
                    readContinuation();
                    break;
                default:
                    next();
                    next();
                    readContinuation();
                    break;
            }
        }

        private void readContinuation() throws Exception {
            out.server();
            while (continuationBytes-- > 0) {
                int next = next();
                if (next == '\n') {
                    out.server();
                }
            }
        }

        private void continuationDigit(int digit) throws Exception {
            continuationBytes = 10 * continuationBytes + digit;
            continuation();
        }

        public void close() throws Exception {
            source.close();
            sump.close();
        }
    }

    private static final class Out {
        private static final String OK_APPEND_COMPLETED = "OK APPEND completed.";

        private static final String[] IGNORE_LINES_STARTING_WITH = {
                "S: \\* OK \\[PERMANENTFLAGS", "C: A22 LOGOUT",
                "S: \\* BYE Logging out", "S: \\* OK Dovecot ready\\." };

        private static final String[] IGNORE_LINES_CONTAINING = {
                "OK Logout completed.", "LOGIN imapuser password",
                "OK Logged in", "LOGOUT" };

        private final CharBuffer lineBuffer = CharBuffer.allocate(131072);

        private boolean isClient = false;

        public void client() {
            lineBuffer.put("C: ");
            isClient = true;
        }

        public void print(char next) {
            if (!isClient) {
                escape(next);
            }
            lineBuffer.put(next);
        }

        private void escape(char next) {
            if (next == '\\' || next == '*' || next == '.' || next == '['
                    || next == ']' || next == '+' || next == '(' || next == ')'
                    || next == '{' || next == '}' || next == '?') {
                lineBuffer.put('\\');
            }
        }

        public void server() {
            lineBuffer.put("S: ");
            isClient = false;
        }

        public void print(String phrase) {
            if (!isClient) {
                phrase = StringUtils.replace(phrase, "\\", "\\\\");
                phrase = StringUtils.replace(phrase, "*", "\\*");
                phrase = StringUtils.replace(phrase, ".", "\\.");
                phrase = StringUtils.replace(phrase, "[", "\\[");
                phrase = StringUtils.replace(phrase, "]", "\\]");
                phrase = StringUtils.replace(phrase, "+", "\\+");
                phrase = StringUtils.replace(phrase, "(", "\\(");
                phrase = StringUtils.replace(phrase, ")", "\\)");
                phrase = StringUtils.replace(phrase, "}", "\\}");
                phrase = StringUtils.replace(phrase, "{", "\\{");
                phrase = StringUtils.replace(phrase, "?", "\\?");
            }
            lineBuffer.put(phrase);
        }

        public void lineEnd() {
            lineBuffer.flip();
            final String text = lineBuffer.toString();
            String[] lines = text.split("\r\n");
            for (String line : lines) {
                String chompedLine = StringUtils.chomp(line);
                if (!ignoreLine(chompedLine)) {
                    final String[] words = StringUtils.split(chompedLine);
                    if (words.length > 3 && "S:".equalsIgnoreCase(words[0]) && "OK".equalsIgnoreCase(words[2])) {
                        final int commandWordIndex;
                        if (words[3] == null || !words[3].startsWith("\\[")) {
                            commandWordIndex = 3;
                        } else {
                            int wordsCount = 3;
                            while (wordsCount < words.length) {
                                if (words[wordsCount++].endsWith("]")) {
                                    break;
                                }
                            }
                            commandWordIndex = wordsCount;
                        }
                        final String command = words[commandWordIndex];
                        final String commandOkPhrase;
                        if ("CREATE".equalsIgnoreCase(command)) {
                            commandOkPhrase = "OK CREATE completed.";
                        } else if ("FETCH".equalsIgnoreCase(command)) {
                            commandOkPhrase = "OK FETCH completed.";
                        } else if ("APPEND".equalsIgnoreCase(command)) {
                            commandOkPhrase = OK_APPEND_COMPLETED;
                        } else if ("DELETE".equalsIgnoreCase(command)) {
                            commandOkPhrase = "OK DELETE completed.";
                        } else if ("STORE".equalsIgnoreCase(command)) {
                            commandOkPhrase = "OK STORE completed.";
                        } else if ("RENAME".equalsIgnoreCase(command)) {
                            commandOkPhrase = "OK RENAME completed.";
                        } else if ("EXPUNGE".equalsIgnoreCase(command)) {
                            commandOkPhrase = "OK EXPUNGE completed.";
                        } else if ("LIST".equalsIgnoreCase(command)) {
                            commandOkPhrase = "OK LIST completed.";
                        } else if ("SELECT".equalsIgnoreCase(command)) {
                            if (commandWordIndex == 3) {
                                commandOkPhrase = "OK SELECT completed.";
                            } else {
                                commandOkPhrase = "OK " + words[3].toUpperCase(Locale.US) + " SELECT completed.";
                            }
                        } else {
                            commandOkPhrase = null;
                        }
                        if (commandOkPhrase != null) {
                            chompedLine = words[0] + " " + words[1] + " " + commandOkPhrase;
                        }
                    }
                    chompedLine = StringUtils.replace(chompedLine, "\\\\Seen \\\\Draft",
                        "\\\\Draft \\\\Seen");
                    chompedLine = StringUtils.replace(chompedLine, "\\\\Flagged \\\\Deleted",
                        "\\\\Deleted \\\\Flagged");
                    chompedLine = StringUtils.replace(chompedLine, "\\\\Flagged \\\\Draft",
                        "\\\\Draft \\\\Flagged");
                    chompedLine = StringUtils.replace(chompedLine, "\\\\Seen \\\\Recent",
                        "\\\\Recent \\\\Seen");
                    chompedLine = StringUtils.replace(chompedLine, "\\] First unseen\\.",
                        "\\](.)*");
                    if (chompedLine.startsWith("S: \\* OK \\[UIDVALIDITY ")) {
                        chompedLine = "S: \\* OK \\[UIDVALIDITY \\d+\\]";
                    } else if (chompedLine.startsWith("S: \\* OK \\[UIDNEXT")) {
                        chompedLine = "S: \\* OK \\[PERMANENTFLAGS \\(\\\\Answered \\\\Deleted \\\\Draft \\\\Flagged \\\\Seen\\)\\]";
                    }

                    System.out.println(chompedLine);
                }
            }
            lineBuffer.clear();
        }

        private boolean ignoreLine(String line) {
            boolean result = Arrays.stream(IGNORE_LINES_CONTAINING)
                .anyMatch(entry -> line.indexOf(entry) > 0);
            for (int i = 0; i < IGNORE_LINES_STARTING_WITH.length && !result; i++) {
                if (line.startsWith(IGNORE_LINES_STARTING_WITH[i])) {
                    result = true;
                    break;
                }
            }
            return result;
        }
    }

    private static final class IgnoreHeaderInputStream extends InputStream {

        private boolean isFinishedHeaders = false;

        private final InputStream delegate;

        public IgnoreHeaderInputStream(InputStream delegate) {
            super();
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            final int result;
            final int next = delegate.read();
            if (isFinishedHeaders) {
                result = next;
            } else {
                switch (next) {
                    case -1:
                        isFinishedHeaders = true;
                        result = next;
                        break;
                    case '#':
                        readLine();
                        result = read();
                        break;

                    case '\r':
                    case '\n':
                    case ' ':
                    case '\t':
                        result = read();
                        break;

                    default:
                        isFinishedHeaders = true;
                        result = next;
                        break;
                }
            }
            return result;
        }

        private void readLine() throws IOException {
            int next = delegate.read();
            while (next != -1 && next != '\r' && next != '\n') {
                next = delegate.read();
            }
        }
    }
}
