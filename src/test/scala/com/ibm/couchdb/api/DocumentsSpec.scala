/*
 * Copyright 2015 IBM Corporation, Google Inc.
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

package com.ibm.couchdb.api

import com.ibm.couchdb.{CouchDoc, Req}
import com.ibm.couchdb.spec.{CouchDbSpecification, SpecConfig}
import monocle.syntax._
import org.http4s.Status

import scala.language.experimental.macros

class DocumentsSpec extends CouchDbSpecification {

  val db        = "couchdb-scala-documents-spec"
  val server    = new Server(client)
  val databases = new Databases(client)
  val documents = new Documents(client, db, typeMapping)

  private def clear() = recreateDb(databases, db)

  clear()

  "Documents API" >> {

    "Create a document with an auto-generated UUID" >> {
      awaitDocOk(documents.create(fixAlice))
    }

    "Create a document with a specified UUID" >> {
      val uuid = awaitRight(server.mkUuid)
      awaitDocOk(documents.create(fixAlice, uuid), uuid)
    }

    "Get a document by a UUID" >> {
      val uuid = awaitRight(server.mkUuid)
      awaitRight(documents.create(fixAlice, uuid))
      awaitRight(documents.get[FixPerson](uuid)).doc mustEqual fixAlice
      val aliceRes = awaitRight(documents.get[FixPerson](uuid))
      aliceRes.doc.name mustEqual fixAlice.name
      aliceRes.doc.age mustEqual fixAlice.age
      aliceRes._id mustEqual uuid
      aliceRes._rev must beRev
    }

    "Get a document by a non-existent UUID" >> {
      val uuid = awaitRight(server.mkUuid)
      awaitError(documents.get[FixPerson](uuid), "not_found")
      awaitError(documents.get[FixPerson](""), "not_found")
    }

    "Create multiple documents in bulk" >> {
      clear()
      val res = awaitRight(documents.createMany(Seq(fixAlice, fixBob)))
      res must haveLength(2)
      checkDocOk(res(0))
      checkDocOk(res(1))
    }

    "Create multiple documents in bulk with IDs" >> {
      clear()
      val docs = Map("1" -> fixAlice, "2" -> fixBob)
      val res = awaitRight(documents.createMany(docs))
      res must haveLength(docs.size)
      res.foreach(checkDocOk)
      res.map(_.id) mustEqual docs.keys.toList
      val created = awaitRight(documents.getMany[FixPerson](docs.keys.toList))
      created.getDocs.map(_.doc) mustEqual docs.values.toSeq
      created.rows.map(_.id) mustEqual docs.keys.toSeq
    }

    "Get all documents" >> {
      clear()
      val created1 = awaitRight(documents.create(fixAlice))
      val created2 = awaitRight(documents.create(fixAlice))
      val docs = awaitRight(documents.getMany.query)
      docs.offset mustEqual 0
      docs.total_rows must beGreaterThanOrEqualTo(2)
      docs.rows must haveLength(2)
      docs.rows.map(_.id) must contain(allOf(created1.id, created2.id))
    }

    "Get multiple documents by IDs" >> {
      clear()
      val createdAlice = awaitRight(documents.create(fixAlice))
      awaitRight(documents.create(fixBob))
      val createdCarl = awaitRight(documents.create(fixCarl))
      val docs = awaitRight(documents.getMany.query(Seq(createdAlice.id, createdCarl.id)))
      docs.offset mustEqual 0
      docs.total_rows mustEqual 3
      docs.rows must haveLength(2)
      docs.rows.map(_.id) mustEqual Seq(createdAlice.id, createdCarl.id)
    }

    "Get multiple documents by IDs with some missing" >> {
      clear()
      val fixPersons = Seq(fixAlice, fixBob, fixCarl)
      val createdPersons = fixPersons.map(person => awaitRight(documents.create(person)))
      val missingIds = Seq("non-existent-id-1", "non-existent-id-2")
      val existingIds = createdPersons.map(_.id)
      val docs = awaitRight(documents.getMany.queryAllowMissing(existingIds ++ missingIds))
      docs.offset mustEqual 0
      docs.rows must haveLength(missingIds.length + existingIds.length)
      docs.rows.flatMap(_.toOption).map(_.id).toList mustEqual existingIds
      docs.rows.flatMap(_.swap.toOption).map(_.key).toList mustEqual missingIds
    }

    "Get all documents and include the doc data" >> {
      clear()
      val created1 = awaitRight(documents.create(fixAlice))
      val created2 = awaitRight(documents.create(fixAlice))
      val docs = awaitRight(documents.getMany.queryIncludeDocs[FixPerson])
      docs.offset mustEqual 0
      docs.total_rows mustEqual 2
      docs.rows must haveLength(2)
      docs.rows.map(_.id) mustEqual Seq(created1.id, created2.id)
      docs.rows.map(_.doc.doc) mustEqual Seq(fixAlice, fixAlice)
      docs.getDocs.map(_.doc) mustEqual Seq(fixAlice, fixAlice)
      docs.getDocsData mustEqual Seq(fixAlice, fixAlice)
    }

    "Get all documents by type and include the doc data" >> {
      clear()
      awaitRight(documents.createMany(Seq(fixAlice, fixBob)))
      val fixXMen = Seq(fixProfessorX, fixMagneto)
      val createdXMen = awaitRight(documents.createMany(fixXMen))
      val docs = awaitRight(documents.getMany.queryByTypeIncludeDocsWithTemporaryView[FixXPerson])
      docs.total_rows mustEqual 4
      docs.rows must haveLength(2)
      docs.rows.map(_.value) mustEqual createdXMen.map(_.id)
      docs.rows.map(_.doc.doc) mustEqual fixXMen
      docs.getDocs.map(_.doc) mustEqual fixXMen
      docs.getDocsData mustEqual fixXMen
    }

    "Get all documents by type and include the doc data, given a permanent type filter view" >> {
      clear()
      val design = new Design(client, db)
      awaitRight(design.create(fixDesign))
      awaitRight(documents.createMany(Seq(fixAlice, fixBob)))
      val fixXMen = Seq(fixProfessorX, fixMagneto)
      val createdXMen = awaitRight(documents.createMany(fixXMen))
      val docs = awaitRight(
                             documents.getMany.
                             queryByTypeIncludeDocs[(String, String), String, FixXPerson]
                               (FixViews.typeFilterView))
      docs.total_rows mustEqual 4
      docs.rows must haveLength(2)
      docs.rows.map(_.value) mustEqual createdXMen.map(_.id)
      docs.rows.map(_.doc.doc) mustEqual fixXMen
      docs.getDocs.map(_.doc) mustEqual fixXMen
      docs.getDocsData mustEqual fixXMen

    }

    "Get multiple documents by IDs and include the doc data" >> {
      clear()
      val createdAlice = awaitRight(documents.create(fixAlice))
      awaitRight(documents.create(fixBob))
      val createdCarl = awaitRight(documents.create(fixCarl))
      val docs = awaitRight(documents.getMany[FixPerson](Seq(createdAlice.id, createdCarl.id)))
      docs.offset mustEqual 0
      docs.total_rows mustEqual 3
      docs.rows must haveLength(2)
      docs.rows.map(_.id) mustEqual Seq(createdAlice.id, createdCarl.id)
      docs.rows.map(_.doc.doc) mustEqual Seq(fixAlice, fixCarl)
      docs.getDocs.map(_.doc) mustEqual Seq(fixAlice, fixCarl)
      docs.getDocsData mustEqual Seq(fixAlice, fixCarl)
    }

    "Get multiple documents by IDs with some missing and include the doc data" >> {
      clear()
      val fixPersons = Seq(fixAlice, fixBob, fixCarl)
      val createdPersons = fixPersons.map(person => awaitRight(documents.create(person)))
      val missingIds = Seq("non-existent-id-1", "non-existent-id-2")
      val existingIds = createdPersons.map(_.id)
      val docs = awaitRight(documents.getMany.queryIncludeDocsAllowMissing[FixPerson](existingIds ++ missingIds))
      docs.offset mustEqual 0
      docs.rows must haveLength(missingIds.length + existingIds.length)
      docs.rows.flatMap(_.toOption).map(_.id).toList mustEqual existingIds
      docs.rows.flatMap(_.toOption).map(_.doc.doc) mustEqual fixPersons
      docs.getDocs.flatMap(_.toOption).map(_.doc) mustEqual fixPersons
      docs.getDocsData mustEqual fixPersons
      docs.rows.flatMap(_.swap.toOption).map(_.key).toList mustEqual missingIds
    }

    "Get a document containing unicode values" >> {
      clear()
      val created1 = awaitRight(documents.create[FixPerson](fixHaile))
      awaitRight(documents.get[FixPerson](created1.id)).doc mustEqual fixHaile
      val created2 = awaitRight(documents.createMany[FixPerson](Seq(fixHaile, fixMagritte)))
      val docs = awaitRight(documents.getMany.queryIncludeDocs[FixPerson](created2.map(_.id)))
      docs.getDocs.map(_.doc) mustEqual Seq(fixHaile, fixMagritte)
    }

    "Update a document" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      val docOk = awaitRight(documents.update(aliceRes applyLens _docPersonAge modify (_ + 1)))
      checkDocOk(docOk, aliceRes._id)
      val aliceRes2 = awaitRight(documents.get[FixPerson](aliceRes._id))
      aliceRes2._id mustEqual aliceRes._id
      aliceRes2._rev mustEqual docOk.rev
      aliceRes2.doc.name mustEqual fixAlice.name
      aliceRes2.doc.age mustEqual fixAlice.age + 1
    }

    "Fail to update a document without ID" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      awaitError(documents.update(aliceRes.copy(_id = "")), "cannot_update")
    }

    "Fail to update a document with an outdated revision" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      await(documents.update(aliceRes applyLens _docPersonAge set 26))
      val error = awaitLeft(documents.update(aliceRes applyLens _docPersonAge set 27))
      error.status mustEqual Status.Conflict
      error.error mustEqual "conflict"
    }

    "Delete a document" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      awaitDocOk(documents.delete(aliceRes), aliceRes._id)
      awaitError(documents.get[FixPerson](aliceRes._id), "not_found")
    }

    "Attach a byte array to a document" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      awaitDocOk(documents.attach(aliceRes, fixAttachmentName, fixAttachmentData), aliceRes._id)
    }

    "Get a byte array attachment" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      awaitDocOk(documents.attach(aliceRes, fixAttachmentName, fixAttachmentData))
      val url = s"http://${SpecConfig.couchDbHost}:${SpecConfig.couchDbPort}" +
        s"/${db}/${aliceRes._id}/${fixAttachmentName}"
      awaitRight(documents.getAttachmentUrl(aliceRes, fixAttachmentName)) mustEqual url
      awaitRight(documents.getAttachment(aliceRes, fixAttachmentName)) mustEqual fixAttachmentData
    }

    "Get a document with attachment stubs" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      awaitDocOk(
        documents.attach(
          aliceRes, fixAttachmentName, fixAttachmentData, fixAttachmentContentType),
        aliceRes._id)
      val doc = awaitRight(documents.get[FixPerson](aliceRes._id))
      doc._id mustEqual aliceRes._id
      doc.doc.name mustEqual aliceRes.doc.name
      doc._attachments must haveLength(1)
      doc._attachments must haveKey(fixAttachmentName)
      val meta = doc._attachments(fixAttachmentName)
      meta.content_type mustEqual fixAttachmentContentType
      meta.length mustEqual fixAttachmentData.length
      meta.stub mustEqual true
      meta.digest must not be empty
    }

    "Get a document with attachments inline" >> {
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      awaitDocOk(
        documents.attach(
          aliceRes, fixAttachmentName, fixAttachmentData, fixAttachmentContentType),
        aliceRes._id)
      val doc = awaitRight(documents.get.attachments().query[FixPerson](aliceRes._id))
      doc._id mustEqual aliceRes._id
      doc.doc.name mustEqual aliceRes.doc.name
      doc._attachments must haveLength(1)
      doc._attachments must haveKey(fixAttachmentName)
      val attachment = doc._attachments(fixAttachmentName)
      attachment.content_type mustEqual fixAttachmentContentType
      attachment.length mustEqual -1
      attachment.stub mustEqual false
      attachment.digest must not be empty
      attachment.toBytes mustEqual fixAttachmentData
    }

    "Create and get a document with attachments" >> {
      val attachments = Map[String, Req.Attachment](
        fixAttachmentName -> Req.Attachment(fixAttachmentData, fixAttachmentContentType),
        fixAttachment2Name -> Req.Attachment(fixAttachment2Data, fixAttachment2ContentType))
      val created = awaitRight(documents.create(fixAlice, attachments))
      val doc = awaitRight(documents.get.attachments().query[FixPerson](created.id))
      doc._id mustEqual created.id
      doc._attachments must haveLength(2)
      doc._attachments must haveKeys(fixAttachmentName, fixAttachment2Name)
      val attachment = doc._attachments(fixAttachmentName)
      attachment.content_type mustEqual fixAttachmentContentType
      attachment.length mustEqual -1
      attachment.stub mustEqual false
      attachment.digest must not be empty
      attachment.toBytes mustEqual fixAttachmentData
      val attachment2 = doc._attachments(fixAttachment2Name)
      attachment2.content_type mustEqual fixAttachment2ContentType
      attachment2.length mustEqual -1
      attachment2.stub mustEqual false
      attachment2.digest must not be empty
      attachment2.toBytes mustEqual fixAttachment2Data
    }

    "Delete an attachment to a document" >> {
      clear()
      val created = awaitRight(documents.create(fixAlice))
      val aliceRes = awaitRight(documents.get[FixPerson](created.id))
      val attachment = awaitRight(documents.attach(aliceRes, fixAttachmentName, fixAttachmentData))
      val aliceWithAttachment = awaitRight(documents.get[FixPerson](created.id))
      awaitDocOk(documents.deleteAttachment(aliceWithAttachment, fixAttachmentName), attachment.id)
      awaitError(documents.getAttachment(aliceRes, fixAttachmentName), "not_found")
    }

    "Bulk update should" >> {
      val fixes = Seq(fixAlice, fixBob, fixHaile)
      def change(v: String): String = s"$v-updated"
      def create(x: Seq[FixPerson]): Seq[CouchDoc[FixPerson]] = {
        clear()
        val newIds = awaitRight(documents.createMany(x)).map(_.id)
        awaitRight(documents.getMany[FixPerson](newIds)).getDocs
      }
      def modify(orig: Seq[CouchDoc[FixPerson]]): Seq[CouchDoc[FixPerson]] =
        orig.map(x => x applyLens _docPersonName modify change)

      def createAndModify: (Seq[FixPerson]) => Seq[CouchDoc[FixPerson]] = create _ andThen modify

      "update all documents when valid Ids and Rev" >> {
        val modified = createAndModify(fixes)
        val updatedDocs = awaitRight(documents.updateMany(modified)).map(_.id)
        updatedDocs.size mustEqual fixes.size
        val updateDocs = awaitRight(documents.getMany[FixPerson](modified.map(_._id)))
        updateDocs.getDocs.map(_.doc) mustEqual fixes.map(x => FixPerson(change(x.name), age = x.age))
      }

      "fail if one or more elements is missing Id" >> {
        val modified = createAndModify(fixes)
        val withInvalidId = modified.updated(2, modified(2).copy(_id = ""))
        awaitError(documents.updateMany(withInvalidId), "cannot_update")
        awaitRight(documents.getMany[FixPerson](modified.map(_._id))).getDocs.map(_.doc) mustEqual fixes
      }

      "fail if one or more elements is missing Rev" >> {
        val modified = createAndModify(fixes)
        val withInvalidRev = modified.updated(1, modified(1).copy(_rev = ""))
        awaitError(documents.updateMany(withInvalidRev), "cannot_update")
        awaitRight(documents.getMany[FixPerson](modified.map(_._id))).getDocs.map(_.doc) mustEqual fixes
      }
    }

    "Bulk delete should" >> {
      val fixes = Seq(fixAlice, fixBob, fixHaile)
      def create(x: Seq[FixPerson]): Seq[CouchDoc[FixPerson]] = {
        clear()
        val newIds = awaitRight(documents.createMany(x)).map(_.id)
        awaitRight(documents.getMany[FixPerson](newIds)).getDocs
      }

      "delete all documents" >> {
        val created = create(fixes)
        val deleted = awaitRight(documents.deleteMany(created)).map(_.id)
        deleted.size mustEqual fixes.size
        val getDeleted = awaitRight(documents.getMany[FixPerson](created.map(_._id)))
        getDeleted.getDocs.size mustEqual fixes.size
        getDeleted.getDocs.count(Option(_).isDefined) mustEqual 0
      }

      "fail if one or more elements is missing an Id" >> {
        val created = create(fixes)
        val withInvalidId = created.updated(1, created(1).copy(_id = ""))
        awaitError(documents.deleteMany(withInvalidId), "cannot_update")
        awaitRight(documents.getMany[FixPerson](created.map(_._id))).getDocs.map(_.doc) mustEqual fixes
      }

      "fail if one or more elements is missing a Rev" >> {
        val created = create(fixes)
        val withInvalidRev = created.updated(2, created(2).copy(_id = ""))
        awaitError(documents.deleteMany(withInvalidRev), "cannot_update")
        awaitRight(documents.getMany[FixPerson](created.map(_._id))).getDocs.map(_.doc) mustEqual fixes
      }
    }
  }
}
