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
package org.totalgrid.reef.models

import org.totalgrid.reef.util.LazyVar
import org.totalgrid.reef.services.core.EntityQuery

import org.squeryl.annotations.Transient
import org.squeryl.PrimitiveTypeMode._
import java.util.UUID
import org.squeryl.Query

import org.totalgrid.reef.client.service.proto.Model

object Point {
  def newInstance(name: String, abnormal: Boolean, dataSource: Option[Entity], _type: Model.PointType, unit: String, uuid: Option[UUID]) = {
    val baseType = _type match {
      case Model.PointType.ANALOG => "Analog"
      case Model.PointType.STATUS => "Status"
      case Model.PointType.COUNTER => "Counter"
    }
    val types = "Point" :: baseType :: Nil
    val ent = EntityQuery.findOrCreateEntity(name, types, uuid)
    val p = new Point(ent.id, _type.getNumber, unit, abnormal)
    dataSource.foreach(ln => { EntityQuery.addEdge(ln, ent, "source"); p.logicalNode.value = Some(ln) })
    p.entity.value = ent
    p
  }

  def findByName(name: String) = findByNames(name :: Nil)
  def findByNames(names: List[String]): Query[Point] = {
    ApplicationSchema.points.where(_.entityId in EntityQuery.findEntityIds(names, List("Point")))
  }
}

case class Point(
    _entityId: UUID,
    pointType: Int,
    unit: String,
    var abnormal: Boolean) extends EntityBasedModel(_entityId) {

  val logicalNode = LazyVar(mayHaveOne(EntityQuery.getParentOfType(entityId, "source", "LogicalNode")))

  /**
   * updated when the abnormal state is changed so we can "tunnel" this update through
   * to the service event.
   * The \@Transient attribute tells squeryl not to put this field in the database
   */
  @Transient
  var abnormalUpdated = false

  val endpoint = LazyVar(logicalNode.value.map(_.asType(ApplicationSchema.endpoints, "LogicalNode")))

  val triggers = LazyVar(ApplicationSchema.triggerSets.where(t => t.pointId === id).toList.map { p => p.point.value = this; p })

  val overrides = LazyVar(ApplicationSchema.overrides.where(t => t.pointId === id).toList.map { p => p.point.value = this; p })
}

object Command {
  def newInstance(name: String, displayName: String, _type: Model.CommandType, uuid: Option[UUID]) = {
    val baseType = _type match {
      case Model.CommandType.CONTROL => "Control"
      case Model.CommandType.SETPOINT_DOUBLE | Model.CommandType.SETPOINT_INT |
        Model.CommandType.SETPOINT_STRING => "Setpoint"
    }
    val ent = EntityQuery.findOrCreateEntity(name, "Command" :: baseType :: Nil, uuid)
    val c = new Command(ent.id, displayName, _type.getNumber, None, None)
    c.entity.value = ent
    c
  }

  def findByNames(names: List[String]): Query[Command] = {
    ApplicationSchema.commands.where(_.entityId in EntityQuery.findEntityIds(names, List("Command")))
  }
  def findIdsByNames(names: List[String]): Query[Long] = {
    from(ApplicationSchema.commands)(c => where(c.entityId in EntityQuery.findEntityIds(names, List("Command"))) select (&(c.id)))
  }
}

case class Command(
    _entityId: UUID,
    val displayName: String,
    val commandType: Int,
    var lastSelectId: Option[Long],
    var triggerId: Option[Long]) extends EntityBasedModel(_entityId) {

  def this() = this(new UUID(0, 0), "", -1, Some(0), Some(0))

  val logicalNode = LazyVar(mayHaveOne(EntityQuery.getParentOfType(entityId, "source", "LogicalNode")))

  val endpoint = LazyVar(logicalNode.value.map(_.asType(ApplicationSchema.endpoints, "LogicalNode")))

  val currentActiveSelect = LazyVar(CommandLockModel.activeSelect(lastSelectId))

  val selectHistory = LazyVar(CommandLockModel.selectsForCommands(id :: Nil))

  val commandHistory = LazyVar(ApplicationSchema.userRequests.where(u => u.commandId === id).toList)
}

object FrontEndPort {
  def newInstance(name: String, network: Option[String], location: Option[String], state: Int, proto: Array[Byte], uuid: Option[UUID]) = {
    val ent = EntityQuery.findOrCreateEntity(name, "Channel" :: Nil, uuid)
    val c = new FrontEndPort(ent.id, network, location, state, proto)
    c.entity.value = ent
    c
  }
}

case class FrontEndPort(
    _entityId: UUID,
    val network: Option[String],
    val location: Option[String],
    val state: Int,
    var proto: Array[Byte]) extends EntityBasedModel(_entityId) {

  def this() = this(new UUID(0, 0), Some(""), Some(""), 0, Array.empty[Byte])

  val endpoints = LazyVar(ApplicationSchema.endpoints.where(ce => ce.frontEndPortId === Some(entityId)).toList)
}

case class ConfigFile(
    _entityId: UUID,
    val mimeType: String,
    var file: Array[Byte]) extends EntityBasedModel(_entityId) {

  val owners = LazyVar(EntityQuery.getParents(entity.value.id, "uses").toList)

  /// this flag allows us to tell if we have modified
  @Transient
  var changedOwners = false
}

case class CommunicationEndpoint(
    _entityId: UUID,
    val protocol: String,
    var frontEndPortId: Option[UUID],
    val dataSource: Boolean) extends EntityBasedModel(_entityId) {

  def this() = this(new UUID(0, 0), "", Some(new UUID(0, 0)), false)
  def this(entityId: UUID, protocol: String, dataSource: Boolean) = this(entityId, protocol, Some(new UUID(0, 0)), dataSource)

  val port = LazyVar(mayHaveOneByEntityUuid(ApplicationSchema.frontEndPorts, frontEndPortId))
  val frontEndAssignment = LazyVar(ApplicationSchema.frontEndAssignments.where(p => p.endpointId === id).single)
  val measProcAssignment = LazyVar(ApplicationSchema.measProcAssignments.where(p => p.endpointId === id).single)

  val configFiles = LazyVar(Entity
    .asType(ApplicationSchema.configFiles, EntityQuery.getChildrenOfType(entity.value.id, "uses", "ConfigurationFile").toList,
      Some("ConfigurationFile")))

  def relationship = if (dataSource) "source" else "sink"

  val points = LazyVar(
    Entity.asType(ApplicationSchema.points, EntityQuery.getChildrenOfType(entity.value.id, relationship, "Point").toList, Some("Point")))
  val commands = LazyVar(
    Entity.asType(ApplicationSchema.commands, EntityQuery.getChildrenOfType(entity.value.id, relationship, "Command").toList, Some("Command")))
}
