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
	"log"
	"net/http"
	"net/http/httputil"

	"github.com/devopsfaith/bloomfilter/rpc/client"
	"github.com/gorilla/mux"
)

var key = "sid"

type LogoutToken struct {
	Token string `json:"logout_token"`
}

func main() {
	server := "krakend:1234"

	c, err := client.New(server)
	if err != nil {
		log.Println("unable to create the rpc client:", err.Error())
		return
	}
	defer c.Close()

	router := mux.NewRouter()
	// Create
	router.HandleFunc("/add", func(w http.ResponseWriter, r *http.Request) {
		addToken(w, r, c)
	}).Methods("POST")

	// Check
	router.HandleFunc("/check/{tokenId}", func(w http.ResponseWriter, r *http.Request) {
		checkToken(w, r, c)
	}).Methods("GET")

	log.Printf("Starting server...")

	log.Fatal(http.ListenAndServe(":8080", router))
}

func addToken(w http.ResponseWriter, r *http.Request, bloomfilter *client.Bloomfilter) {
	// Save a copy of this request for debugging.
	requestDump, err := httputil.DumpRequest(r, true)
	if err != nil {
		log.Println(err)
	}
	log.Println(string(requestDump))

	var logoutToken LogoutToken
	json.NewDecoder(r.Body).Decode(&logoutToken)

	subject := key + "-" + logoutToken.Token
	bloomfilter.Add([]byte(subject))
	log.Printf("adding [%s] %s", key, subject)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusNoContent)
}

func checkToken(w http.ResponseWriter, r *http.Request, bloomfilter *client.Bloomfilter) {
	w.Header().Set("Content-Type", "application/json")
	params := mux.Vars(r)
	tokenID := params["tokenId"]
	subject := key + "-" + tokenID

	res, err := bloomfilter.Check([]byte(subject))
	if err != nil {
		log.Println("Unable to check:", err.Error())
		w.WriteHeader(http.StatusBadRequest)
		return
	}
	log.Printf("checking [%s] %s => %v", key, subject, res)

	json.NewEncoder(w).Encode(res)
}