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

package main

import (
	"encoding/json"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strings"

	"github.com/devopsfaith/bloomfilter/rpc/client"
	"github.com/dgrijalva/jwt-go"
	"github.com/gorilla/mux"
)

func getEnvironmentVariable(envVariable string, defaultVariable string) string {
	result, ok := os.LookupEnv(envVariable)
	if !ok {
		return defaultVariable
	}
	return result
}

var key = getEnvironmentVariable("JWT_CLAIM", "sid")

func main() {
	jwtRevokerPort := ":" + getEnvironmentVariable("JWT_REVOKER_PORT", "8080")

	router := mux.NewRouter()
	// Create
	router.HandleFunc("/add", addToken).Methods("POST")

	// Check
	router.HandleFunc("/check/{tokenId}", checkToken).Methods("GET")

	log.Printf("Starting server...")

	log.Fatal(http.ListenAndServe(jwtRevokerPort, router))
}

func addToken(responseWriter http.ResponseWriter, request *http.Request) {
	// Read request body
	buffer, errorBody := ioutil.ReadAll(request.Body)
	defer request.Body.Close()
	if errorBody != nil {
		log.Fatal("Error reading request body: ", errorBody.Error())
		http.Error(responseWriter, "Error reading request body: " + errorBody.Error(), http.StatusInternalServerError)
		return
	}

	// Extract logout token from body request
	var logoutToken = strings.Split(string(buffer), "=")[1]

	// Parsing logout token
	token, _, errorParse := new(jwt.Parser).ParseUnverified(logoutToken, jwt.MapClaims{})
	if errorParse != nil {
		log.Fatal("Error parsing logout_token: ", errorParse)
		http.Error(responseWriter, "Error parsing logout_token: " + errorParse.Error(), http.StatusInternalServerError)
		return
	}

	// Fetching claims
	claims, success := token.Claims.(jwt.MapClaims)
	if !success {
		log.Fatal("Error when getting token claims")
		http.Error(responseWriter, "Error when getting token claims", http.StatusInternalServerError)
		return
	}

	// Sending sid claim to bloomfilter
	subject := key + "-" + claims[key].(string)

	bloomfilter, errorConnect := connectToBloomfilter()
  if errorConnect != nil {
    http.Error(responseWriter, "Error connecting to the rpc client: " + errorConnect.Error(), http.StatusInternalServerError)
    return
  }
  defer bloomfilter.Close()

	bloomfilter.Add([]byte(subject))
	log.Printf("adding [%s] %s", key, subject)

	responseWriter.Header().Set("Content-Type", "application/json")
	responseWriter.WriteHeader(http.StatusNoContent)
}

func checkToken(responseWriter http.ResponseWriter, request *http.Request) {
	responseWriter.Header().Set("Content-Type", "application/json")
	params := mux.Vars(request)
	tokenID := params["tokenId"]
	subject := key + "-" + tokenID

	bloomfilter, errorConnect := connectToBloomfilter()
  if errorConnect != nil {
    http.Error(responseWriter, "Error connecting to the rpc client: " + errorConnect.Error(), http.StatusInternalServerError)
    return
  }
  defer bloomfilter.Close()

	res, err := bloomfilter.Check([]byte(subject))
	if err != nil {
		log.Println("Unable to check:", err.Error())
		responseWriter.WriteHeader(http.StatusBadRequest)
		return
	}

	json.NewEncoder(responseWriter).Encode(res)
}

func connectToBloomfilter() (*client.Bloomfilter, error) {
	krakendHost := getEnvironmentVariable("KRAKEND_HOST", "krakend")
	krakendPort := getEnvironmentVariable("KRAKEND_PORT", "1234")

	krakendServer := krakendHost + ":" + krakendPort

	bloomfilter, errorConnect := client.New(krakendServer)
	if errorConnect != nil {
		log.Println("Unable to create the rpc client:", errorConnect.Error())
		return nil, errorConnect
	}
	return bloomfilter, nil
}
