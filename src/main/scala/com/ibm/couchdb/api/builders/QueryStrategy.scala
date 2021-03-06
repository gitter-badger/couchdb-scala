/*
 * Copyright 2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.couchdb.api.builders

import com.ibm.couchdb.Req
import com.ibm.couchdb.core.Client
import org.http4s.Status
import upickle.default.Aliases.{R, W}
import upickle.default._

import scalaz.concurrent.Task

trait QueryStrategy {

  def postQuery[Q: R](client: Client, db: String, url: String,
                      ps: Map[String, String]): Task[Q] = {
    client.get[Q](url, Status.Ok, ps.toSeq)
  }

  def queryByIds[K: W, Q: R](client: Client, db: String, url: String, ids: Seq[K],
                             ps: Map[String, String]): Task[Q] = {
    postQuery[Req.DocKeys[K], Q](client, db, url, Req.DocKeys(ids), ps)
  }

  def postQuery[B: W, Q: R](client: Client, db: String, url: String, body: B,
                            ps: Map[String, String]): Task[Q] = {
    client.post[B, Q](url, Status.Ok, body, ps.toSeq)
  }
}
