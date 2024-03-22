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

package org.apache.james.webadmin.routes;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static spark.Spark.halt;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Joiner;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;
import spark.utils.StringUtils;

public class SieveScriptRoutes implements Routes {

    public static final String ROOT_PATH = "/sieve";
    public static final String SCRIPTS = "scripts";
    private static final String USER_NAME = "userName";
    private static final String SCRIPT_NAME = "scriptName";
    private static final String ACTIVATE_PARAMS = "activate";
    private static final String USER_SCRIPT_PATH = Joiner.on(SEPARATOR)
        .join(ROOT_PATH, ":" + USER_NAME, SCRIPTS, ":" + SCRIPT_NAME);

    private final SieveRepository sieveRepository;
    private final UsersRepository usersRepository;

    @Inject
    public SieveScriptRoutes(SieveRepository sieveRepository, UsersRepository usersRepository) {
        this.sieveRepository = sieveRepository;
        this.usersRepository = usersRepository;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        defineAddActiveSieveScript(service);
    }

    public void defineAddActiveSieveScript(Service service) {
        service.put(USER_SCRIPT_PATH, this::addActiveSieveScript);
    }

    private HaltException addActiveSieveScript(Request request, Response response) throws UsersRepositoryException, QuotaExceededException, StorageException, ScriptNotFoundException {
        Username username = extractUser(request);
        ScriptName script = extractScriptName(request);
        boolean isActivated = isActivated(request.queryParams(ACTIVATE_PARAMS));
        sieveRepository.putScript(username, script, extractSieveScriptFromRequest(request));
        if (isActivated) {
            sieveRepository.setActive(username, script);
        }
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private Username extractUser(Request request) throws UsersRepositoryException {
        Username userName = Optional.ofNullable(request.params(USER_NAME))
            .map(String::trim)
            .filter(StringUtils::isNotEmpty)
            .map(Username::of)
            .orElseThrow(() -> throw400withInvalidArgument("Invalid username"));

        if (!usersRepository.contains(userName)) {
            throw404("User not found");
        }
        return userName;
    }

    private ScriptName extractScriptName(Request request) {
        return Optional.ofNullable(request.params(SCRIPT_NAME))
            .map(String::trim)
            .filter(StringUtils::isNotEmpty)
            .map(ScriptName::new)
            .orElseThrow(() -> throw400withInvalidArgument("Invalid Sieve script name"));
    }

    private ScriptContent extractSieveScriptFromRequest(Request request) {
        return new ScriptContent(request.body());
    }

    private boolean isActivated(String activateParam) {
        return Optional.ofNullable(activateParam)
            .map(String::trim)
            .map(this::parseActivateParam)
            .orElse(false);
    }

    private boolean parseActivateParam(String activateParam) {
        if (activateParam.equalsIgnoreCase(Boolean.TRUE.toString())
            || activateParam.equalsIgnoreCase(Boolean.FALSE.toString())) {
            return Boolean.parseBoolean(activateParam);
        }

        throw throw400withInvalidArgument("Invalid activate query parameter");
    }

    private HaltException throw400withInvalidArgument(String message) {
        throw ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message(message)
            .haltError();
    }

    private HaltException throw404(String message) {
        throw ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message(message)
            .haltError();
    }
}
