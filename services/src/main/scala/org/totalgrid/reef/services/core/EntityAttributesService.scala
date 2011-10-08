/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the GNU Affero General Public License
 * Version 3.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.services.core

import org.totalgrid.reef.proto.Model.{ Entity => EntityProto, EntityAttributes => AttrProto }
import org.totalgrid.reef.proto.Utils.Attribute
import org.totalgrid.reef.proto.Descriptors

import org.totalgrid.reef.sapi.client.Response
import org.totalgrid.reef.japi.BadRequestException
import org.totalgrid.reef.japi.Envelope.Status
import org.totalgrid.reef.sapi.BasicRequestHeaders
import org.totalgrid.reef.sapi.service.SyncServiceBase

import org.totalgrid.reef.models.{ Entity, ApplicationSchema, EntityAttribute => AttrModel }

import scala.collection.JavaConversions._

import org.squeryl.PrimitiveTypeMode.inTransaction
import java.util.UUID

class EntityAttributesService extends SyncServiceBase[AttrProto] {
  import EntityAttributesService._

  override val descriptor = Descriptors.entityAttributes

  override def put(req: AttrProto, env: BasicRequestHeaders): Response[AttrProto] = {
    if (!req.hasEntity)
      throw new BadRequestException("Must specify Entity in request.")

    inTransaction {
      val entEntry = EntityQueryManager.findEntity(req.getEntity) getOrElse { throw new BadRequestException("Entity does not exist.") }

      val existingAttrs = entEntry.attributes.value
      val withoutIds = existingAttrs.map { a => a.id = 0; a }
      val newAtttributes = req.getAttributesList.map { convertProtoToEntry(entEntry.id, _) }.toList

      val differences = newAtttributes.diff(withoutIds)

      if (!differences.isEmpty || newAtttributes.size != existingAttrs.size) {
        deleteAllFromEntity(entEntry.id)
        ApplicationSchema.entityAttributes.insert(newAtttributes)
        // since changed the entities we need to manually update the lazy var
        entEntry.attributes.value = newAtttributes

        val status = if (existingAttrs.isEmpty) Status.CREATED else Status.UPDATED
        Response(status, protoFromEntity(entEntry) :: Nil)
      } else {
        Response(Status.NOT_MODIFIED, protoFromEntity(entEntry) :: Nil)
      }
    }
  }

  override def delete(req: AttrProto, env: BasicRequestHeaders): Response[AttrProto] = {
    if (!req.hasEntity)
      throw new BadRequestException("Must specify Entity in request.")

    inTransaction {
      val entEntry = EntityQueryManager.findEntity(req.getEntity) getOrElse { throw new BadRequestException("Entity does not exist.") }

      val existingAttrs = entEntry.attributes.value

      val status = if (!existingAttrs.isEmpty) {
        deleteAllFromEntity(entEntry.id)
        Status.DELETED
      } else {
        Status.NOT_MODIFIED
      }
      Response(status, protoFromEntity(entEntry) :: Nil)
    }
  }

  override def get(req: AttrProto, env: BasicRequestHeaders): Response[AttrProto] = {
    if (!req.hasEntity)
      throw new BadRequestException("Must specify Entity in request.")

    inTransaction {
      Response(Status.OK, queryEntities(req.getEntity))
    }
  }

}

object EntityAttributesService {
  import org.squeryl.PrimitiveTypeMode._
  import org.totalgrid.reef.proto.OptionalProtos._
  import com.google.protobuf.ByteString

  def deleteAllFromEntity(entityId: UUID) = {
    ApplicationSchema.entityAttributes.deleteWhere(t => t.entityId === entityId)
  }

  def queryEntities(proto: EntityProto): List[AttrProto] = {
    val join = if (proto.hasUuid && proto.getUuid.getUuid == "*") {
      allJoin
    } else if (proto.hasUuid) {
      uidJoin(proto.getUuid.getUuid)
    } else if (proto.hasName) {
      nameJoin(proto.getName)
    } else {
      throw new BadRequestException("Must search for entities by uid or name.")
    }

    if (join.isEmpty)
      throw new BadRequestException("No entities match request.")

    val pairs = join.groupBy { case (ent, attr) => ent }.toList

    pairs.map {
      case (ent, tupleList) =>
        val attrList = tupleList.map(_._2)
        protoFromEntity(ent, attrList.toList.flatten)
    }
  }

  def uidJoin(uid: String): List[(Entity, Option[AttrModel])] = {
    join(ApplicationSchema.entities, ApplicationSchema.entityAttributes.leftOuter)((ent, attr) =>
      where(ent.id === UUID.fromString(uid))
        select (ent, attr)
        on (Some(ent.id) === attr.map(_.entityId))).toList
  }

  def nameJoin(name: String): List[(Entity, Option[AttrModel])] = {
    join(ApplicationSchema.entities, ApplicationSchema.entityAttributes.leftOuter)((ent, attr) =>
      where(ent.name === name)
        select (ent, attr)
        on (Some(ent.id) === attr.map(_.entityId))).toList
  }

  def allJoin: List[(Entity, Option[AttrModel])] = {
    join(ApplicationSchema.entities, ApplicationSchema.entityAttributes.leftOuter)((ent, attr) =>
      select(ent, attr)
        on (Some(ent.id) === attr.map(_.entityId))).toList
  }

  def protoFromEntity(entry: Entity): AttrProto = {
    AttrProto.newBuilder
      .setEntity(EntityQueryManager.entityToProto(entry))
      .addAllAttributes(entry.attributes.value.map(convertToProto(_)))
      .build
  }

  def protoFromEntity(entry: Entity, attrList: List[AttrModel]): AttrProto = {
    AttrProto.newBuilder
      .setEntity(EntityQueryManager.entityToProto(entry))
      .addAllAttributes(attrList.map(convertToProto(_)))
      .build
  }

  def convertToProto(entry: AttrModel) = {
    val proto = Attribute.newBuilder
      .setName(entry.attrName)

    entry.boolVal.foreach { v =>
      proto.setVtype(Attribute.Type.BOOL)
      proto.setValueBool(v)
    }
    entry.stringVal.foreach { v =>
      proto.setVtype(Attribute.Type.STRING)
      proto.setValueString(v)
    }
    entry.longVal.foreach { v =>
      proto.setVtype(Attribute.Type.SINT64)
      proto.setValueSint64(v)
    }
    entry.doubleVal.foreach { v =>
      proto.setVtype(Attribute.Type.DOUBLE)
      proto.setValueDouble(v)
    }
    entry.byteVal.foreach { v =>
      proto.setVtype(Attribute.Type.BYTES)
      proto.setValueBytes(ByteString.copyFrom(v))
    }

    proto.build
  }

  def createEntryFromProto(entityId: UUID, attr: Attribute) = {
    ApplicationSchema.entityAttributes.insert(convertProtoToEntry(entityId, attr))
  }

  def convertProtoToEntry(entityId: UUID, attr: Attribute) = {
    val attrName = attr.getName
    val attrTyp = attr.getVtype

    var stringVal: Option[String] = None
    var boolVal: Option[Boolean] = None
    var longVal: Option[Long] = None
    var doubleVal: Option[Double] = None
    var byteVal: Option[Array[Byte]] = None

    def typExcept(typ: String) = new BadRequestException("Type " + typ + " specified but no " + typ + " value.")

    attrTyp match {
      case Attribute.Type.STRING =>
        val v = attr.valueString getOrElse { throw typExcept("string") }
        stringVal = Some(v)
      case Attribute.Type.BOOL =>
        val v = attr.valueBool getOrElse { throw typExcept("boolean") }
        boolVal = Some(v)
      case Attribute.Type.SINT64 =>
        val v = attr.valueSint64 getOrElse { throw typExcept("long") }
        longVal = Some(v)
      case Attribute.Type.DOUBLE =>
        val v = attr.valueDouble getOrElse { throw typExcept("double") }
        doubleVal = Some(v)
      case Attribute.Type.BYTES =>
        val v = attr.valueBytes getOrElse { throw typExcept("byte array") }
        byteVal = Some(v.toByteArray)
    }

    new AttrModel(entityId, attrName, stringVal, boolVal, longVal, doubleVal, byteVal)
  }
}

