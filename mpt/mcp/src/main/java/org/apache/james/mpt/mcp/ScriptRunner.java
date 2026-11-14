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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.protocol.FileProtocolSessionBuilder;
import org.apache.james.mpt.protocol.ProtocolSession;

/**
 * Parses and runs MPT scripts against the configured targets.
 */
public class ScriptRunner {
    public static final String SCRIPT_NAME = "script";

    public enum Status {
        PASSED, FAILED, ERROR, TIMEOUT
    }

    public record ValidationResult(boolean valid, int sessionCount, Optional<String> error) {
    }

    public record RunRequest(String script, Optional<String> target, Map<Integer, String> sessionTargets,
                             Map<String, String> variables, boolean continueAfterFailure, Optional<Duration> timeout) {
    }

    public record RunReport(Status status, long durationMs, Map<Integer, String> sessionTargets, List<String> failures,
                            Optional<String> error, Map<String, String> capturedVariables, Transcript transcript) {
    }

    private final McpMptConfiguration configuration;
    private final SSLSocketFactory sslSocketFactory;
    private final Semaphore concurrentRuns;

    public ScriptRunner(McpMptConfiguration configuration) throws Exception {
        this.configuration = configuration;
        this.sslSocketFactory = sslSocketFactory(configuration.trustAllCertificates());
        this.concurrentRuns = new Semaphore(configuration.maxConcurrentRuns());
    }

    public McpMptConfiguration configuration() {
        return configuration;
    }

    public ValidationResult validate(String script) {
        try {
            ProtocolSession session = parse(script);
            return new ValidationResult(true, session.getSessionCount(), Optional.empty());
        } catch (Exception e) {
            return new ValidationResult(false, 0, Optional.of(e.getMessage()));
        }
    }

    private ProtocolSession parse(String script) throws Exception {
        ProtocolSession session = new ProtocolSession();
        new FileProtocolSessionBuilder()
            .addProtocolLinesFromStream(new ByteArrayInputStream(script.getBytes(UTF_8)), session, SCRIPT_NAME);
        return session;
    }

    /**
     * @throws IllegalArgumentException on invalid requests (parse error, unknown target...)
     */
    public RunReport run(RunRequest request) throws Exception {
        ProtocolSession protocolSession;
        try {
            protocolSession = parse(request.script());
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        Map<Integer, Target> targets = resolveTargets(request, protocolSession.getSessionCount());
        Duration timeout = request.timeout()
            .filter(duration -> !duration.isNegative() && !duration.isZero())
            .map(duration -> duration.compareTo(configuration.maxScriptTimeout()) > 0 ? configuration.maxScriptTimeout() : duration)
            .orElse(configuration.defaultScriptTimeout());

        Map<String, String> initialVariables = new LinkedHashMap<>(configuration.variables());
        byte[] runId = new byte[4];
        new SecureRandom().nextBytes(runId);
        initialVariables.put("runId", HexFormat.of().formatHex(runId));
        initialVariables.putAll(request.variables());
        initialVariables.forEach(protocolSession::setVariable);
        protocolSession.setContinueAfterFailure(request.continueAfterFailure());

        if (!concurrentRuns.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Too many concurrent runs, try again later");
        }
        Transcript transcript = new Transcript(configuration.maxTranscriptLines());
        Session[] sessions = new Session[targets.size()];
        for (int i = 0; i < sessions.length; i++) {
            sessions[i] = new SocketSession(i, targets.get(i), sslSocketFactory, !configuration.trustAllCertificates(),
                configuration.connectTimeout(), configuration.readTimeout(), transcript);
        }
        Map<Integer, String> targetNames = new LinkedHashMap<>();
        targets.forEach((index, target) -> targetNames.put(index, target.name()));

        long start = System.nanoTime();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Status status;
        Optional<String> error = Optional.empty();
        try {
            Future<?> execution = executor.submit(() -> {
                for (Session session : sessions) {
                    session.start();
                }
                protocolSession.runSessions(sessions);
                return null;
            });
            try {
                execution.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                status = protocolSession.getFailures().isEmpty() ? Status.PASSED : Status.FAILED;
            } catch (TimeoutException e) {
                execution.cancel(true);
                status = Status.TIMEOUT;
                error = Optional.of("Script did not complete within " + timeout.toSeconds() + " seconds");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ProtocolSession.InvalidServerResponseException) {
                    status = Status.FAILED;
                } else {
                    status = Status.ERROR;
                }
                error = Optional.of(describe(cause));
            }
        } finally {
            for (Session session : sessions) {
                try {
                    session.stop();
                } catch (Exception e) {
                    // ignore
                }
            }
            executor.shutdownNow();
            concurrentRuns.release();
        }

        Map<String, String> captured = new LinkedHashMap<>();
        protocolSession.getVariables().forEach((name, value) -> {
            if (!configuration.variables().containsKey(name) && !request.variables().containsKey(name)) {
                captured.put(name, value);
            }
        });
        List<String> failures = new ArrayList<>(protocolSession.getFailures());
        return new RunReport(status, (System.nanoTime() - start) / 1_000_000, targetNames, failures, error, captured, transcript);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message.trim());
    }

    private Map<Integer, Target> resolveTargets(RunRequest request, int sessionCount) {
        Optional<String> defaultTarget = request.target()
            .or(() -> configuration.targets().size() == 1
                ? Optional.of(configuration.targets().keySet().iterator().next())
                : Optional.empty());
        request.sessionTargets().keySet().forEach(index -> {
            if (index < 0 || index >= sessionCount) {
                throw new IllegalArgumentException("Session " + index + " is not used by the script (sessions: 0.." + (sessionCount - 1) + ")");
            }
        });
        Map<Integer, Target> result = new LinkedHashMap<>();
        for (int i = 0; i < sessionCount; i++) {
            int index = i;
            String name = Optional.ofNullable(request.sessionTargets().get(i))
                .or(() -> defaultTarget)
                .orElseThrow(() -> new IllegalArgumentException("No target given for session " + index
                    + ". Available targets: " + configuration.targets().keySet()));
            Target target = configuration.targets().get(name);
            if (target == null) {
                throw new IllegalArgumentException("Unknown target '" + name + "'. Available targets: " + configuration.targets().keySet());
            }
            result.put(i, target);
        }
        return result;
    }

    private static SSLSocketFactory sslSocketFactory(boolean trustAll) throws Exception {
        if (!trustAll) {
            return SSLContext.getDefault().getSocketFactory();
        }
        TrustManager trustAllManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {trustAllManager}, new SecureRandom());
        return context.getSocketFactory();
    }
}
