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

import org.totalgrid.reef.proto.Alarms._
import org.totalgrid.reef.proto.Events.{ Event => EventProto }
import org.totalgrid.reef.models.{ EventConfigStore, ApplicationSchema, AlarmModel, EventStore }
import org.totalgrid.reef.services.framework._

import org.totalgrid.reef.messaging.ProtoSerializer._
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Table
import org.totalgrid.reef.proto.OptionalProtos._
import org.totalgrid.reef.messaging.serviceprovider.{ ServiceEventPublishers, ServiceSubscriptionHandler }
import org.totalgrid.reef.proto.Descriptors
import org.totalgrid.reef.proto.OptionalProtos._
import org.totalgrid.reef.japi.{ BadRequestException, Envelope }
import org.totalgrid.reef.sapi.RequestEnv
import org.totalgrid.reef.services.{ ServiceDependencies, ProtoRoutingKeys }

// implicit proto properties
import SquerylModel._
import org.totalgrid.reef.util.Optional._

class AlarmService(protected val model: AlarmServiceModel)
    extends SyncModeledServiceBase[Alarm, AlarmModel, AlarmServiceModel]
    with DefaultSyncBehaviors {

  override val descriptor = Descriptors.alarm

  // Alarms are created by events. No create via an Alarm proto.
  override def preCreate(context: RequestContext, req: Alarm) = {
    if (!req.hasEvent)
      throw new BadRequestException("If creating alarm you must also specify a full event")
    req
  }

  // If they don't have a state, what are they doing with an update?
  override def preUpdate(context: RequestContext, proto: ServiceType, existing: ModelType) = {
    if (!proto.hasState)
      throw new BadRequestException("AlarmService update is for changing alarm state, but there is no state field in this proto.")

    proto
  }
}

class AlarmServiceModel(summary: SummaryPoints)
    extends SquerylServiceModel[Alarm, AlarmModel]
    with EventedServiceModel[Alarm, AlarmModel]
    with AlarmConversion with AlarmSummaryCalculations {

  // delayed link to eventServiceModel to break circular dependecy
  var eventModel: Option[EventServiceModel] = None

  override def getEventProtoAndKey(alarm: AlarmModel) = {
    val (_, eventKeys) = EventConversion.makeEventProtoAndKey(alarm.event.value)
    val proto = convertToProto(alarm)
    val keys = eventKeys.map { ProtoRoutingKeys.generateRoutingKey(proto.uid :: Nil) + "." + _ }

    (proto, keys)
  }

  override def getRoutingKey(req: Alarm): String = throw new Exception("bad interface")
  override def getSubscribeKeys(req: Alarm): List[String] = {
    val eventKeys = EventConversion.makeSubscribeKeys(req.event.getOrElse(EventProto.newBuilder.build))

    eventKeys.map { ProtoRoutingKeys.generateRoutingKey(req.uid :: Nil) + "." + _ }
  }

  override def createFromProto(context: RequestContext, req: Alarm): AlarmModel = {

    val entity = req.event.entity.map(EntityQueryManager.findEntity(_)).getOrElse(None)

    val eventProto = req.getEvent

    eventModel.get.validateEventProto(eventProto)

    if (!eventProto.hasSeverity || !eventProto.hasRendered || !eventProto.hasUserId || !req.hasState)
      throw new BadRequestException("When posting alarm the event needs to have severity, rendered, userId and state fields set")

    val (eventStore, alarmOption) = eventModel.get.makeEvent(context, EventConfigStore.ALARM, eventProto, eventProto.getSeverity,
      entity, eventProto.getRendered, eventProto.getUserId, req.getState.getNumber)
    alarmOption.get
  }

  def createAlarmForEvent(context: RequestContext, eventStore: EventStore, alarmState: Int) = {
    val alarm = new AlarmModel(alarmState, eventStore.id)
    alarm.event.value = eventStore // so we don't lookup the event again
    create(context, alarm)
  }

  // Update an Alarm. Currently, only the state can be updated.
  // Enforce valid state transitions.
  //
  override def updateFromProto(context: RequestContext, proto: Alarm, existing: AlarmModel): (AlarmModel, Boolean) = {

    if (existing.isNextStateValid(proto.getState.getNumber))
      update(context, updateModelEntry(proto, existing), existing)
    else {
      // TODO: access the proto to print the state names in the exception.
      throw new BadRequestException("Invalid state transistion from " + Alarm.State.valueOf(existing.state) + " to " + proto.getState, Envelope.Status.BAD_REQUEST)
    }
  }
  // Don't allow any updates except on the alarm state.
  private def updateModelEntry(proto: Alarm, existing: AlarmModel): AlarmModel = {
    new AlarmModel(
      proto.getState.getNumber,
      existing.eventUid) // Use the existing event so there's no possibility of an update.
  }

  /// hooks to feed the populated models to the summary counter
  override def postCreate(context: RequestContext, created: AlarmModel): Unit = {
    super.postCreate(context, created)
    updateSummaries(created, None, summary.incrementSummary _)
  }
  override def postUpdate(context: RequestContext, updated: AlarmModel, original: AlarmModel): Unit = {
    super.postUpdate(context, updated, original)
    updateSummaries(updated, Some(original), summary.incrementSummary _)
  }
}

/**
 * keeps a running tally of unacked alarms in the system.
 */
trait AlarmSummaryCalculations {

  // TODO: get summary point names from configuration
  def severityName(n: Int) = "summary.unacked_alarms_severity_" + n
  def subsystemName(n: String) = "summary.unacked_alarms_subsystem_" + n
  def eqGroupName(n: String) = "summary.unacked_alarms_equipment_group_" + n

  def initializeSummaries(summary: SummaryPoints) {
    val severities = (for (i <- 1 to 3) yield i).toList
    val subsystems = List("FEP", "Processing")
    val eqGroups = EntityQueryManager.findEntitiesByType("EquipmentGroup" :: Nil).toList.map { _.name }

    // TODO: get list of summary points from configuration somewhere
    val names = severities.map { severityName(_) } ::: subsystems.map { subsystemName(_) } ::: eqGroups.map { eqGroupName(_) }

    // look through all unacked alarms to regenerate counts
    val results = from(ApplicationSchema.alarms, ApplicationSchema.events)((alarm, event) =>
      where(alarm.state in List(AlarmModel.UNACK_AUDIBLE, AlarmModel.UNACK_SILENT) and
        alarm.eventUid === event.id)
        select (alarm, event))

    val alarms = AlarmQueries.populate(results.toList)

    // build a map with the intial values
    val m = scala.collection.mutable.Map.empty[String, Int]
    names.foreach(m(_) = 0) // start with 0
    alarms.foreach(updateSummaries(_, None, { (name, value) =>
      m.get(name) match {
        case Some(prev) => m(name) = prev + value
        case None => m(name) = value
      }
    }))

    // publish the initial values only once
    m.foreach(e => summary.setSummary(e._1, e._2))
  }

  def updateSummaries(alarm: AlarmModel, previous: Option[AlarmModel], func: (String, Int) => Any) {

    // if we are counting for the integrity its either +1 or 0, if updating an event its either
    // 0, -1, or 1 depending on state changes
    var incr = if (alarm.isUnacked && !(previous.isDefined && previous.get.isUnacked)) 1 else 0
    if (!alarm.isUnacked && previous.isDefined && previous.get.isUnacked) incr = -1

    if (incr == 0) return

    // TODO: publish by category instead of subsystem
    func(severityName(alarm.event.value.severity), incr)
    func(subsystemName(alarm.event.value.subsystem), incr)
    alarm.event.value.groups.value.foreach(ent => func(eqGroupName(ent.name), incr))
  }

}
object AlarmSummaryCalculations extends AlarmSummaryCalculations

import org.totalgrid.reef.messaging.AMQPProtoFactory
import org.totalgrid.reef.executor.Executor
import org.totalgrid.reef.services.ProtoServiceCoordinator

class AlarmSummaryInitializer(model: AlarmServiceModel, summary: SummaryPoints) extends ProtoServiceCoordinator with AlarmSummaryCalculations {

  def addAMQPConsumers(amqp: AMQPProtoFactory, reactor: Executor) {
    reactor.execute {
      model.initializeSummaries(summary)
    }
  }
}

/**
 * Trait for coordinating between service message types and data model type
 */
trait AlarmConversion
    extends AlarmQueries {

  val table = ApplicationSchema.alarms

  // Did the update change anything that requires a notification to bus
  // subscribers.
  //
  // We cannot update the event contained within this Alarm, just the state
  // of the Alarm.
  //
  def isModified(entry: AlarmModel, existing: AlarmModel): Boolean = {
    entry.state != existing.state
  }

  def createModelEntry(proto: Alarm): AlarmModel = {
    new AlarmModel(
      proto.getState.getNumber,
      proto.getEvent.getUid.toLong)
  }

  def convertToProto(entry: AlarmModel): Alarm = {
    convertToProto(entry, entry.event.value)
  }

  def convertToProto(entry: AlarmModel, event: EventStore): Alarm = {
    Alarm.newBuilder
      .setUid(entry.id.toString)
      .setState(Alarm.State.valueOf(entry.state))
      .setEvent(EventConversion.convertToProto(event))
      .build
  }
}
object AlarmConversion extends AlarmConversion

import org.squeryl.dsl.ast.{ LogicalBoolean, BinaryOperatorNodeLogicalBoolean }
import org.squeryl.dsl.ast.{ OrderByArg, ExpressionNode }
import org.squeryl.PrimitiveTypeMode._

trait AlarmQueries {

  def searchQuery(proto: Alarm, sql: AlarmModel): List[LogicalBoolean] = {

    // by default we dont return removed items to a get request
    val defaultStateList = List(AlarmModel.UNACK_AUDIBLE, AlarmModel.UNACK_SILENT, AlarmModel.ACKNOWLEDGED)
    val stateList = proto.state.map { _.getNumber :: Nil }.getOrElse(defaultStateList)

    (Some(sql.state in stateList) :: Nil).flatten
  }

  def uniqueQuery(proto: Alarm, sql: AlarmModel): List[LogicalBoolean] = {
    (proto.uid.asParam(sql.id === _.toLong) :: Nil).flatten // if exists, use it.
  }

  def searchEventQuery(event: EventStore, select: Option[EventProto]): List[LogicalBoolean] = {
    select.map(EventConversion.searchQuery(_, event).flatten) getOrElse (Nil)
  }
  def uniqueEventQuery(event: EventStore, select: Option[EventProto]): List[LogicalBoolean] = {
    select.map(EventConversion.uniqueQuery(_, event).flatten) getOrElse (Nil)
  }

  def findRecords(context: RequestContext, req: Alarm): List[AlarmModel] = {

    val query = from(ApplicationSchema.alarms, ApplicationSchema.events)((alarm, event) =>
      where(SquerylModel.combineExpressions(uniqueQuery(req, alarm) :::
        uniqueEventQuery(event, req.event) :::
        searchQuery(req, alarm) :::
        searchEventQuery(event, req.event)) and
        alarm.eventUid === event.id)
        select ((alarm, event))
        orderBy (new OrderByArg(event.time).desc)).page(0, 50)

    populate(query.toList)
  }

  def populate(results: List[(AlarmModel, EventStore)]): List[AlarmModel] = {
    // TODO: figure out why this totally hoses squeryl
    //    val results = from(ApplicationSchema.alarms, ApplicationSchema.events, ApplicationSchema.entities)((alarm, event, entity) =>
    //        where(buildQuery(req, alarm) and
    //          optionalEventQuery(event, req.event) and
    //          alarm.eventUid === event.id and event.entityId === entity.id)
    //          select ((alarm, event, entity))
    //          orderBy (new OrderByArg(event.time).desc)).page(0, 50)

    val entityIds = results.map { _._2.entityId }.flatten
    val entities = from(ApplicationSchema.entities)(entity => where(entity.id in entityIds) select (entity)).toList
    results.map { case (a, evt) => { if (evt.entityId.isDefined) { evt.entity.value = entities.find(_.id == evt.entityId.get) }; a.event.value = evt; a } }
  }

  def findRecord(context: RequestContext, req: Alarm): Option[AlarmModel] = {
    val query = from(ApplicationSchema.alarms, ApplicationSchema.events)((alarm, event) =>
      where(SquerylModel.combineExpressions(uniqueQuery(req, alarm) :::
        uniqueEventQuery(event, req.event)) and
        alarm.eventUid === event.id)
        select ((alarm, event))
        orderBy (new OrderByArg(event.time).desc)).page(0, 50)

    val uniqueItems = populate(query.toList)
    uniqueItems.size match {
      case 0 => None
      case 1 => Some(uniqueItems.head)
      case _ => throw new Exception("Unique query returned " + uniqueItems.size + " entries")
    }
  }
}
object AlarmQueries extends AlarmQueries
