/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Green Energy Corp licenses this file
 * to you under the GNU Affero General Public License Version 3.0
 * (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.loader

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import org.totalgrid.reef.util.{ Logging, XMLHelper }
import org.totalgrid.reef.loader.equipment._
import org.totalgrid.reef.loader.configuration._
import org.totalgrid.reef.proto.Model.{ Entity, Relationship, EntityEdge, Point, Command }
import org.totalgrid.reef.proto.Processing._
import org.totalgrid.reef.messaging.SyncServiceClient

/**
 * EquipmentLoader loads the logical model.
 *
 * TODO: generic_type is not set
 */
class EquipmentLoader(client: SyncServiceClient) extends Logging {

  val equipmentProfiles = HashMap[String, EquipmentType]()
  val pointProfiles = HashMap[String, PointProfile]()
  val commands = HashMap[String, Command]()
  val commandEntities = HashMap[String, Entity]()

  // map of points to units
  val equipmentPointUnits = HashMap[String, String]()

  /**
   * Load this equipment node and all children. Create edges to connect the children.
   * Return equipmentPointUnits - map of points to units
   */
  def load(model: EquipmentModel, actionModel: HashMap[String, ActionSet]): HashMap[String, String] = {

    info("Start")
    // Collect all the profiles in name->profile maps.
    val profiles = model.getProfiles
    if (profiles != null) {
      profiles.getPointProfile.toList.foreach(pointProfile => pointProfiles += (pointProfile.getName -> pointProfile))
      profiles.getEquipmentProfile.toList.foreach(equipmentProfile => equipmentProfiles += (equipmentProfile.getName -> equipmentProfile))
    }

    println("Loading Equipment: Found PointProfiles: " + pointProfiles.keySet.mkString(", "))
    println("Loading Equipment: Found EquipmentProfiles: " + equipmentProfiles.keySet.mkString(", "))

    model.getEquipment.toList.foreach(e => {
      println("Loading Equipment: processing equipment '" + e.getName + "'")
      loadEquipment(e, "", actionModel)
    })

    info("End")

    equipmentPointUnits
  }

  /**
   * Load this equipment node and all children. Create edges to connect the children.
   */
  private def loadEquipment(equipment: Equipment, namePrefix: String, actionModel: HashMap[String, ActionSet]): Entity = {
    val name = namePrefix + equipment.getName
    val childPrefix = name + "."

    // IMPORTANT:
    // profiles is a list of profiles plus this equipment (as the last "profile" in the list)
    //  TODO: We don't see profiles within profiles. Could go recursive and map each profile name to a list of profiles.

    val profiles: List[EquipmentType] = equipment.getEquipmentProfile.toList.map(p => equipmentProfiles(p.getName)) ::: List[EquipmentType](equipment)
    info("load equipment '" + name + "' with profiles: " + profiles.map(_.getName).dropRight(1).mkString(", ")) // don't print last profile which is this equipment
    val entity = toEntity(name, profiles)
    client.put(entity)

    // Load all the children and create the edges
    trace("load equipment: " + name + " children")
    val children = profiles.flatMap(_.getEquipment).map(loadEquipment(_, childPrefix, actionModel))
    children.foreach(child => client.put(toEntityEdge(entity, child, "owns")))

    // Commands are controls and setpoints. TODO: setpoints
    trace("load equipment: " + name + " commands")
    val commands = profiles.flatMap(_.getControl.toList).map(c => processControl(childPrefix + c.getName, entity))

    // Points
    trace("load equipment: " + name + " points")
    val statuses = profiles.flatMap(_.getStatus.toList).map(processPointType(_, entity, childPrefix, actionModel))
    val analogs = profiles.flatMap(_.getAnalog.toList).map(processPointType(_, entity, childPrefix, actionModel))
    val counters = profiles.flatMap(_.getCounter.toList).map(processPointType(_, entity, childPrefix, actionModel))

    entity
  }

  /**
   * Process controls defined under equipment.
   */
  def processControl(name: String, entity: Entity) = {
    import ProtoUtils._

    trace("processControl: " + name)
    val commandEntity = toEntityType(name, List("Command"))
    val command = toCommand(name, commandEntity)
    commandEntities += (name -> commandEntity)
    commands += (name -> command)

    client.put(commandEntity)
    client.put(command)
    client.put(toEntityEdge(entity, commandEntity, "owns"))

    commandEntity
  }

  /**
   * Put the point and pointEntity and setup the owns relationship.
   * Return the pointEntity
   *
   * TODO: Handle exceptions from invalid name references (ex: control)
   */
  def processPointType(pointT: PointType, entity: Entity, childPrefix: String, actionModel: HashMap[String, ActionSet]): Entity = {
    import ProtoUtils._

    val name = childPrefix + pointT.getName
    trace("processPointType: " + name)
    val pointEntity = toEntityType(name, List("Point"))
    val point = toPoint(name, pointEntity)
    client.put(pointEntity)
    client.put(point)
    client.put(toEntityEdge(entity, pointEntity, "owns"))

    val unit = getAttribute[String](name, pointT, _.isSetUnit, _.getUnit, "unit")
    equipmentPointUnits += (name -> unit)

    val controls = getElements[Control](name, pointT, _.getControl.toList)
    controls.map(c => client.put(toEntityEdge(pointEntity, getCommandEntity(name, childPrefix + c.getName), "feedback")))

    // Insert range triggers to the existing trigger set in the system. Overwrite any triggers with the same name.
    // Do not clear the current TriggerSet.
    //
    val ranges = getElements[Range](name, pointT, _.getRange.toList)
    if (!ranges.isEmpty) {

      var triggerSets = client.get(toTriggerSet(point))
      var triggerSet = triggerSets.size match {
        case 0 => TriggerSet.newBuilder.setPoint(point)
        case 1 => triggerSets.head.toBuilder
        case n => throw new Exception("Service returned multiple TriggerSets for point '" + name + "'. Should be one or none.")
      }
      println("processPointType " + name + ": inserting triggers: ")
      ranges.foreach(range => triggerSet = insertTrigger(triggerSet, toTrigger(name, range, unit, actionModel)))
      val ts = triggerSet.build
      println("processPointType " + name + ": final triggerSet: \n" + ts.toString)
      client.put(ts)
    }

    // TODO: process valueMap

    pointEntity
  }

  /**
   * Get a point attribute in the point or the referenced pointProfile.
   * For this function, profiles can be nested and it will get the first on it
   * finds.
   * Throw exception if no attribute found.
   */
  def getAttribute[A](
    name: String,
    point: PointType,
    isSet: (PointType) => Boolean,
    get: (PointType) => A,
    attributeName: String): A = {

    val value: A = isSet(point) match {
      case true => get(point)
      case false =>
        point.isSetPointProfile match {
          case true => getAttribute[A](name, getPointProfile(name, point), isSet, get, attributeName)
          case false => throw new Exception("Point '" + name + "' is missing required attribute '" + attributeName + "'.")
        }
    }
    value
  }

  /**
   * get contained elements from this point and its pointProfile
   */
  def getElements[A](name: String, point: PointType, get: (PointType) => List[A]): List[A] = {

    if (point.isSetPointProfile)
      get(point) ::: getElements[A](name, getPointProfile(name, point), get)
    else
      get(point)
  }

  def getCommandEntity(elementName: String, commandName: String): Entity = {
    if (commandEntities.contains(commandName))
      commandEntities(commandName)
    else
      throw new Exception("control '" + commandName + "' referenced from '" + elementName + "' was not found in configuration.")
  }

  def getPointProfile(elementName: String, point: PointType): PointProfile = {
    val p = point.getPointProfile
    if (pointProfiles.contains(p))
      pointProfiles(p)
    else
      throw new Exception("pointProfile '" + p + "' referenced from '" + elementName + "' was not found in configuration.")
  }

  /**
   * Return an Entity proto
   */
  def toEntity(name: String, profiles: List[EquipmentType]): Entity = {
    val proto = Entity.newBuilder
      .setName(name)
    val types = profiles.flatMap(_.getType.toList)
    if (types.isEmpty)
      throw new Exception(name + " needs at least one <type> specified in the Equipment Model.")
    types.foreach(typ => proto.addTypes(typ.getName))

    //profiles.foreach( p => p.getType.toList.foreach(typ => proto.addTypes(typ.getName)) )
    //profiles.foreach( p => applyEquipmentTypes( proto, p))

    proto.build
  }

  /**
   * Commands are controls and setpoints. TODO: setpoints
   */
  def toCommand(name: String, entity: Entity): Command = {
    val proto = Command.newBuilder
      .setName(name)
      .setEntity(entity)

    proto.build
  }

  def toEntityEdge(parent: Entity, child: Entity, relationship: String): EntityEdge = {
    val proto = EntityEdge.newBuilder
      .setParent(parent)
      .setChild(child)
      .setRelationship(relationship)

    proto.build
  }
}

