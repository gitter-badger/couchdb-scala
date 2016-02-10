/*
 * Copyright 2015 IBM Corporation
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

import com.ibm.couchdb._
import com.ibm.couchdb.core.Client
import upickle.default.Aliases.{R, W}
import upickle.default.write

import scalaz.concurrent.Task

case class ViewQueryBuilder[K, V](client: Client,
                                  db: String,
                                  design: String,
                                  view: String,
                                  params: Map[String, String] = Map.empty[String, String])
                                 (implicit
                                  kr: R[K],
                                  kw: W[K],
                                  vr: R[V],
                                  cdr: R[CouchKeyVals[K, V]],
                                  dkw: W[Req.DocKeys[K]]) {

  def conflicts(conflicts: Boolean = true): ViewQueryBuilder[K, V] = {
    set("conflicts", conflicts)
  }

  def descending(descending: Boolean = true): ViewQueryBuilder[K, V] = {
    set("descending", descending)
  }

  def endKey[K2: W](endKey: K2): ViewQueryBuilder[K, V] = {
    set("endkey", write(endKey))
  }

  def endKeyDocId(endKeyDocId: String): ViewQueryBuilder[K, V] = {
    set("endkey_docid", endKeyDocId)
  }

  def group(group: Boolean = true): ViewQueryBuilder[K, V] = {
    set("group", group)
  }

  def groupLevel(groupLevel: Int): ViewQueryBuilder[K, V] = {
    set("group_level", groupLevel)
  }

  private def includeDocs(includeDocs: Boolean = true): ViewQueryBuilder[K, V] = {
    set("include_docs", includeDocs)
  }

  def attachments(attachments: Boolean = true): ViewQueryBuilder[K, V] = {
    set("attachments", attachments)
  }

  def attEncodingInfo(attEncodingInfo: Boolean = true): ViewQueryBuilder[K, V] = {
    set("att_encoding_info", attEncodingInfo)
  }

  def inclusiveEnd(inclusiveEnd: Boolean = true): ViewQueryBuilder[K, V] = {
    set("inclusive_end", inclusiveEnd)
  }

  def key[D: W](key: D): ViewQueryBuilder[K, V] = {
    set("key", write(key))
  }

  def limit(limit: Int): ViewQueryBuilder[K, V] = {
    set("limit", limit)
  }

  private def reduce(reduce: Boolean = true): ViewQueryBuilder[K, V] = {
    set("reduce", reduce)
  }

  def skip(skip: Int): ViewQueryBuilder[K, V] = {
    set("skip", skip)
  }

  def stale(stale: String): ViewQueryBuilder[K, V] = {
    set("stale", stale)
  }

  def startKey[K2: W](startKey: K2): ViewQueryBuilder[K, V] = {
    set("startkey", write(startKey))
  }

  def startKeyDocId(startKeyDocId: String): ViewQueryBuilder[K, V] = {
    set("startkey_docid", startKeyDocId)
  }

  def updateSeq(updateSeq: Boolean = true): ViewQueryBuilder[K, V] = {
    set("update_seq", updateSeq)
  }

  private def set(key: String, value: String): ViewQueryBuilder[K, V] = {
    copy(params = params.updated[String](key, value))(kr, kw, vr, cdr, dkw)
  }

  private def set(key: String, value: Any): ViewQueryBuilder[K, V] = {
    set(key, value.toString)
  }

  def query: Task[CouchKeyVals[K, V]] = queryWithoutIds[CouchKeyVals[K, V]](params)

  def queryWithReduce[K2: R, V2: R]: Task[CouchReducedKeyVals[K2, V2]] = {
    queryWithoutIds[CouchReducedKeyVals[K2, V2]](reduce().params)
  }

  def query(keys: Seq[K]): Task[CouchKeyVals[K, V]] = queryByIds[CouchKeyVals[K, V]](keys, params)

  def queryWithReduce[K2: R, V2: R](keys: Seq[K]): Task[CouchReducedKeyVals[K2, V2]] = {
    queryByIds[CouchReducedKeyVals[K2, V2]](keys, reduce().group().params)
  }

  def queryIncludeDocs[D: R]: Task[CouchDocs[K, V, D]] = queryWithoutIds[CouchDocs[K, V, D]](includeDocs().params)

  def queryIncludeDocs[D: R](keys: Seq[K]): Task[CouchDocs[K, V, D]] = {
    queryByIds[CouchDocs[K, V, D]](keys, includeDocs().params)
  }

  def queryWithoutIds[Q: R](ps: Map[String, String]): Task[Q] = {
    QueryStrategy.query[Q](client, db, s"/$db/_design/$design/_view/$view", ps)
  }

  def queryByIds[Q: R](ids: Seq[K], ps: Map[String, String]): Task[Q] = {
    QueryStrategy.queryByIds[K, Q](client, db, s"/$db/_design/$design/_view/$view", ids, ps)
  }
}
