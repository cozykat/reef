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

import org.totalgrid.reef.proto.Model.{ Entity => EntityProto }
import org.totalgrid.reef.proto.Descriptors
import org.totalgrid.reef.proto.OptionalProtos._
import org.totalgrid.reef.services.core.util.UUIDConversions._

import org.squeryl.PrimitiveTypeMode._

import scala.collection.JavaConversions._
import org.totalgrid.reef.sapi.RequestEnv
import org.totalgrid.reef.sapi.client.Response
import org.totalgrid.reef.sapi.service.SyncServiceBase
import org.totalgrid.reef.japi.{ BadRequestException }
import org.totalgrid.reef.japi.Envelope.Status
import org.totalgrid.reef.models.Entity

class EntityService extends SyncServiceBase[EntityProto] {

  override val descriptor = Descriptors.entity

  override def put(protoRequest: EntityProto, env: RequestEnv): Response[EntityProto] = {

    inTransaction {
      if (!protoRequest.hasName || protoRequest.getTypesCount == 0) {
        throw new BadRequestException("Must include name and atleast one entity type to create entity.")
      }

      val typ = protoRequest.getTypesList.head
      val name = protoRequest.getName
      val list = EntityQueryManager.nameTypeQuery(Some(name), Some(List(typ)))

      var (status, ent) = list match {
        case List(ent, _) => throw new BadRequestException("more than one entity matched: " + name + " type:" + typ)
        case List(ent) => (Status.NOT_MODIFIED, list.head)
        case Nil => (Status.CREATED, EntityQueryManager.addEntity(name, typ :: Nil, protoRequest.uuid))
      }

      protoRequest.getTypesList.tail.foreach(t => {
        if (ent.types.value.find(_ == t).isEmpty) {
          ent = EntityQueryManager.addTypeToEntity(ent, t)
          if (status == Status.NOT_MODIFIED) status = Status.UPDATED
        }
      })
      Response(status, EntityQueryManager.entityToProto(ent).build :: Nil)
    }
  }

  override def get(req: EntityProto, env: RequestEnv): Response[EntityProto] = {
    inTransaction {
      EntityQueryManager.checkAllTypesInSystem(req)
      val result = EntityQueryManager.fullQuery(req)
      Response(Status.OK, result)
    }
  }

  override def delete(req: EntityProto, env: RequestEnv): Response[EntityProto] = {
    // TODO: cannot delete entities with "built in types" repersentations still around
    inTransaction {
      val entities = EntityQueryManager.fullQueryAsModels(req);

      val (results, status) = entities match {
        case Nil => (req :: Nil, Status.NOT_MODIFIED)
        case l: List[Entity] =>
          EntityQueryManager.deleteEntities(entities)
          (entities.map { EntityQueryManager.entityToProto(_).build }, Status.DELETED)
      }
      Response(status, results)
    }
  }
}

import org.totalgrid.reef.proto.Model.{ EntityEdge => EntityEdgeProto }
import org.totalgrid.reef.models.{ EntityEdge }

class EntityEdgeService extends SyncServiceBase[EntityEdgeProto] {

  override val descriptor = Descriptors.entityEdge

  def convertToProto(entry: EntityEdge): EntityEdgeProto = {
    val b = EntityEdgeProto.newBuilder()
    import org.totalgrid.reef.services.framework.SquerylModel._
    b.setParent(EntityProto.newBuilder.setUuid(makeUuid(entry.parentId)))
    b.setChild(EntityProto.newBuilder.setUuid(makeUuid(entry.childId)))
    b.setRelationship(entry.relationship)
    b.build
  }

  override def put(req: EntityEdgeProto, env: RequestEnv): Response[EntityEdgeProto] = {

    inTransaction {
      val parentEntity = EntityQueryManager.findEntity(req.getParent).getOrElse(throw new BadRequestException("cannot find parent"))
      val childEntity = EntityQueryManager.findEntity(req.getChild).getOrElse(throw new BadRequestException("cannot find child"))
      val existingEdge = EntityQueryManager.findEdge(parentEntity, childEntity, req.getRelationship)

      val (edge, status) = existingEdge match {
        case Some(edge) => (edge, Status.NOT_MODIFIED)
        case None => (EntityQueryManager.addEdge(parentEntity, childEntity, req.getRelationship), Status.CREATED)
      }
      val proto = convertToProto(edge)
      Response(status, proto :: Nil)
    }
  }

  override def delete(req: EntityEdgeProto, env: RequestEnv): Response[EntityEdgeProto] = {

    inTransaction {

      val existingEdge: Option[EntityEdge] = EntityQueryManager.findEntity(req.getParent).flatMap { parent =>
        EntityQueryManager.findEntity(req.getChild).flatMap { child =>
          EntityQueryManager.findEdge(parent, child, req.getRelationship)
        }
      }

      val (proto, status) = existingEdge match {
        case Some(edge) =>
          EntityQueryManager.deleteEdge(edge)
          (convertToProto(edge), Status.DELETED)
        case None => (req, Status.NOT_MODIFIED)
      }
      Response(status, proto :: Nil)
    }
  }

  override def get(req: EntityEdgeProto, env: RequestEnv): Response[EntityEdgeProto] = {
    inTransaction {
      // TODO: add edge searching
      val edges = EntityQueryManager.edges.where(t => true === true).toList
      Response(Status.OK, edges.map { convertToProto(_) })
    }
  }
}
