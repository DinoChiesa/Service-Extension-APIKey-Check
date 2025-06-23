// Copyright © 2025 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

/* jshint esversion:9, strict:implied, node:true */
/* global process */

import express from "express";
import logging from "morgan";
import os from "node:os";
import df from "dateformat";
import consoleTimestamp from "console-stamp";

const app = express();
const gVersion = "20250621-1019";
const PORT = process.env.PORT || 8080;
const k_service = process.env.K_SERVICE || "-unknown-";
const k_revision = process.env.K_REVISION || "-unknown-";
const runningInCloudRun = () => process.env.K_SERVICE && process.env.K_REVISION;
const runningLocally = () => !runningInCloudRun();

let logFormat = ":method :url :status :res[content-length] - :response-time ms";

if (runningLocally()) {
  consoleTimestamp(console, {
    format: ":date(yyyy/mm/dd HH:MM:ss.l) :label",
  });
  logFormat = ":mydate " + logFormat;
  logging.token("mydate", function (_req, _res) {
    return df(new Date(), "[yyyy/mm/dd HH:MM:ss.l]");
  });
}

app.use(logging(logFormat));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.set("json spaces", 2);
app.set("trust proxy", true); // for X-Forwarded-For header from Google CLB?

const serviceAccount = await (async function () {
  if (!runningInCloudRun()) {
    return "-not available-";
  }
  //  get the service account the Cloud Run service is running as
  let response = await fetch(
    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email",
    {
      method: "GET",
      headers: { "Metadata-Flavor": "Google" },
    },
  );
  return await response.text();
})();

function unhandledRequest(_req, response, _next) {
  response
    .status(400)
    .header("Content-Type", "application/json")
    .send(
      JSON.stringify(
        { error: "unhandled request", message: "try GET/POST/PUT" },
        null,
        2,
      ) + "\n",
    )
    .end();
}

function statusRequestHandler(request, response, next) {
  // parse the Authorization header
  const authz = request.header("Authorization");
  let parts = authz && authz.split(" ");
  const apikey = parts && parts.length == 2 && parts[1];

  response.header("Content-Type", "application/json");
  const body = {
    app: {
      version: gVersion,
      port: PORT,
      k_service,
      k_revision,
      serviceAccount,
    },
    caller: {
      apikey,
    },
    engines: {
      node: process.versions.node,
      v8: process.versions.v8,
    },
    os: {
      platform: os.platform(),
      type: os.type(),
      release: os.release(),
      userInfo: os.userInfo(),
    },
    milliseconds_since_epoch: new Date().getTime(),
  };

  response
    .header("x-powered-by", "node/express")
    .status(200)
    .send(JSON.stringify(body, null, 2) + "\n");
}

// Register the handlers
app.get("/status", statusRequestHandler);
app.get("/status/:any", statusRequestHandler);
app.get("/", statusRequestHandler);
app.use(unhandledRequest);

const appinstance = app.listen(PORT, function () {
  console.log("Server Listening on " + appinstance.address().port);
});
