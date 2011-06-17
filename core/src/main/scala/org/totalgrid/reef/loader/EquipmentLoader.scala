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
package org.totalgrid.reef.loader

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.loader.configuration._
import org.totalgrid.reef.proto.Processing._
import org.totalgrid.reef.proto.Model.{ Entity, EntityEdge, Command => CommandProto }
import org.totalgrid.reef.proto.Model.{ PointType => PointTypeProto, CommandType => CommandTypeProto }
import org.totalgrid.reef.loader.equipment._

/**
 * EquipmentLoader loads the logical model.
 *
 * TODO: generic_type is not set
 */
class EquipmentLoader(client: ModelLoader, loadCache: LoadCacheEqu, ex: ExceptionCollector) extends Logging {

  val equipmentProfiles = HashMap[String, EquipmentType]()
  val pointProfiles = HashMap[String, PointProfile]()
  val commands = HashMap[String, CommandProto]()
  val commandEntities = HashMap[String, Entity]()

  // map of points to units
  val equipmentPointUnits = HashMap[String, String]()

  /**
   * Reset all class variables
   */
  def reset: Unit = {
    equipmentProfiles.clear
    pointProfiles.clear
    commands.clear
    commandEntities.clear
    equipmentPointUnits.clear
  }

  /**
   * Load this equipment node and all children. Create edges to connect the children.
   * Return equipmentPointUnits - map of points to units
   */
  def load(model: EquipmentModel, actionModel: HashMap[String, ActionSet]): HashMap[String, String] = {

    logger.info("Start")

    ex.collect("Equipment Profiles: ") {
      // Collect all the profiles in name->profile maps.
      Option(model.getProfiles).foreach { p =>
        p.getPointProfile.toList.foreach(pointProfile => pointProfiles += (pointProfile.getName -> pointProfile))
        p.getEquipmentProfile.toList.foreach(equipmentProfile => equipmentProfiles += (equipmentProfile.getName -> equipmentProfile))
      }
    }

    println("Loading Equipment: Found PointProfiles: " + pointProfiles.keySet.mkString(", "))
    println("Loading Equipment: Found EquipmentProfiles: " + equipmentProfiles.keySet.mkString(", "))

    model.getEquipment.toList.foreach(e => {
      println("Loading Equipment: processing equipment '" + e.getName + "'")
      ex.collect("Equipment: " + e.getName) {
        loadEquipment(e, "", actionModel)
      }
    })

    logger.info("End")

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
    logger.info("load equipment '" + name + "' with profiles: " + profiles.map(_.getName).dropRight(1).mkString(", ")) // don't print last profile which is this equipment

    val childEquipment = profiles.flatMap(_.getEquipment)
    val controls = profiles.flatMap(_.getControl.toList)
    val setpoints = profiles.flatMap(_.getSetpoint.toList)
    val statuses = profiles.flatMap(_.getStatus.toList)
    val analogs = profiles.flatMap(_.getAnalog.toList)
    val counters = profiles.flatMap(_.getCounter.toList)

    val points = statuses ::: analogs ::: counters
    val commands = controls ::: setpoints

    var extraTypes: List[String] = Nil
    if (points.nonEmpty || commands.nonEmpty) extraTypes ::= "Equipment"
    if (childEquipment.nonEmpty) extraTypes ::= "EquipmentGroup"

    val entity = toEntity(name, profiles, extraTypes)
    client.putOrThrow(entity)

    // Commands are controls and setpoints
    logger.trace("load equipment: " + name + " commands")
    controls.map { c =>
      processCommand(childPrefix, c, entity, CommandTypeProto.CONTROL)
      //val display = Option(c.getDisplayName) getOrElse c.getName
      //processCommand(childPrefix + c.getName, display, "Control" :: getTypeList(c.getType), entity)
    }

    setpoints.map { c =>
      // TODO: loader needs to handle setpoint int
      processCommand(childPrefix, c, entity, CommandTypeProto.SETPOINT_DOUBLE)
      //val display = Option(c.getDisplayName) getOrElse c.getName
      //processCommand(childPrefix + c.getName, display, "Setpoint" :: getTypeList(c.getType), entity)
    }

    // Points
    logger.trace("load equipment: " + name + " points")
    points.map(processPointType(_, entity, childPrefix, actionModel))

    // Load all the children and create the edges
    logger.trace("load equipment: " + name + " children")
    val children = childEquipment.map(loadEquipment(_, childPrefix, actionModel))
    children.foreach(child => client.putOrThrow(toEntityEdge(entity, child, "owns")))

    entity
  }

  /**
   * Process controls defined under equipment.
   */
  def processCommand(childPrefix: String, c: Command, equipmentEntity: Entity, commandType: CommandTypeProto) = {
    import ProtoUtils._

    val name = childPrefix + c.getName
    val displayName = Option(c.getDisplayName) getOrElse c.getName

    val baseType = if (commandType == CommandTypeProto.CONTROL) "Control" else "Setpoint"
    val types = baseType :: getTypeList(c.getType)

    logger.trace("processControl: " + name)
    loadCache.addControl(name)
    val commandEntity = toEntityType(name, "Command" :: types)
    val command = toCommand(name, displayName, commandEntity, commandType)
    commandEntities += (name -> commandEntity)
    commands += (name -> command)

    client.putOrThrow(commandEntity)
    client.putOrThrow(command)
    client.putOrThrow(toEntityEdge(equipmentEntity, commandEntity, "owns"))

    commandEntity
  }

  def getTypeList(list: java.util.List[Type]): List[String] = Option(list) match {
    //import scala.collection.JavaConversions._
    case Some(x) => x.map(_.getName).toList
    case None => Nil
  }

  /**
   * Put the point and pointEntity and setup the owns relationship.
   * Return the pointEntity
   *
   * TODO: Handle exceptions from invalid name references (ex: control)
   */
  def processPointType(pointT: PointType, equipmentEntity: Entity, childPrefix: String, actionModel: HashMap[String, ActionSet]): Entity = {
    import ProtoUtils._

    val (baseType, pointProtoType) = pointT match {
      case c: equipment.Analog => ("Analog", PointTypeProto.ANALOG)
      case c: equipment.Status => ("Status", PointTypeProto.STATUS)
      case c: equipment.Counter => ("Counter", PointTypeProto.COUNTER)
      case _ => throw new LoadingException("Bad point type")
    }

    val name = childPrefix + pointT.getName
    val types = "Point" :: baseType :: getTypeList(pointT.getType)

    val unit = getAttribute[String](name, pointT, _.isSetUnit, _.getUnit, "unit")
    equipmentPointUnits += (name -> unit)

    logger.trace("processPointType: " + name)
    val pointEntity = toEntityType(name, types)
    val point = toPoint(name, pointEntity, pointProtoType, unit)
    client.putOrThrow(pointEntity)
    client.putOrThrow(point)
    client.putOrThrow(toEntityEdge(equipmentEntity, pointEntity, "owns"))

    val controls = getElements[Control](name, pointT, _.getControl.toList)
    controls.map(c => client.putOrThrow(toEntityEdge(pointEntity, getCommandEntity(name, childPrefix + c.getName), "feedback")))

    val setpoint = getElements[Setpoint](name, pointT, _.getSetpoint.toList)
    setpoint.map(c => client.putOrThrow(toEntityEdge(pointEntity, getCommandEntity(name, childPrefix + c.getName), "feedback")))

    loadCache.addPoint(name, unit)
    // Insert range triggers to the existing trigger set in the system. Overwrite any triggers with the same name.
    // Do not clear the current TriggerSet.
    //

    var triggers = List.empty[Trigger.Builder]

    val ranges = getElements[Range](name, pointT, _.getRange.toList)
    triggers = triggers ::: ranges.map { range => toTrigger(name, range, unit, actionModel) }

    val unexpectedValues = getElements[Unexpected](name, pointT, _.getUnexpected.toList)
    triggers = triggers ::: unexpectedValues.map { unexpected => toTrigger(name, unexpected, unit, actionModel) }

    val convertValues = getElements[Transform](name, pointT, _.getTransform.toList)
    triggers = triggers ::: convertValues.map { transform => toTrigger(name, transform, unit) }

    if (!triggers.isEmpty) addTriggers(client, point, triggers)

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
          case false => throw new LoadingException("Point '" + name + "' is missing required attribute '" + attributeName + "'.")
        }
    }
    value
  }

  /**
   * get contained elements from this point and its pointProfile
   */
  def getElements[A](name: String, point: PointType, get: PointType => List[A]): List[A] = {

    if (point.isSetPointProfile)
      get(point) ::: getElements[A](name, getPointProfile(name, point), get)
    else
      get(point)
  }

  def getCommandEntity(elementName: String, commandName: String): Entity = {
    if (commandEntities.contains(commandName))
      commandEntities(commandName)
    else
      throw new LoadingException("control '" + commandName + "' referenced from '" + elementName + "' was not found in configuration.")
  }

  def getPointProfile(elementName: String, point: PointType): PointProfile = {
    val p = point.getPointProfile
    if (pointProfiles.contains(p))
      pointProfiles(p)
    else
      throw new LoadingException("pointProfile '" + p + "' referenced from '" + elementName + "' was not found in configuration.")
  }

  /**
   * Return an Entity proto
   */
  def toEntity(name: String, profiles: List[EquipmentType], extraTypes: List[String]): Entity = {
    val proto = Entity.newBuilder
      .setName(name)
    val types = profiles.flatMap(_.getType.toList).map { _.getName }
    if (types.isEmpty)
      throw new LoadingException(name + " needs at least one <type> specified in the Equipment Model.")

    //    extraTypes.foreach { extra =>
    //      if (types.contains(extra))
    //        println("DEPRECATION WARNING: \"" + extra + "\" type is automatically added to " + name)
    //    }

    val finalTypes = (types ::: extraTypes).distinct

    finalTypes.foreach(typ => proto.addTypes(typ))

    //profiles.foreach( p => p.getType.toList.foreach(typ => proto.addTypes(typ.getName)) )
    //profiles.foreach( p => applyEquipmentTypes( proto, p))

    proto.build
  }

  /**
   * Commands are controls and setpoints. TODO: setpoints
   */
  def toCommand(name: String, displayName: String, entity: Entity, commandType: CommandTypeProto): CommandProto = {
    val proto = CommandProto.newBuilder
      .setName(name)
      .setDisplayName(displayName)
      .setEntity(entity)
      .setType(commandType)

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

