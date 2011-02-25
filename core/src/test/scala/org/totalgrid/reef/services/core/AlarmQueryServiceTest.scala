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
package org.totalgrid.reef.services.core

import org.totalgrid.reef.proto.Events._
import org.totalgrid.reef.proto.Alarms._
import org.totalgrid.reef.proto.Model.{ Entity => EntityProto }

import org.scalatest.{ FunSuite, BeforeAndAfterAll }
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.fixture.FixtureSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import scala.collection.JavaConversions._
import org.totalgrid.reef.messaging.serviceprovider.SilentEventPublishers
import org.totalgrid.reef.api.Envelope

// proto list to scala list

import org.squeryl.{ Schema, Table, KeyedEntity }
import org.squeryl.PrimitiveTypeMode._

import org.totalgrid.reef.persistence.squeryl.{ DbConnector, DbInfo }
import org.totalgrid.reef.models._
import org.totalgrid.reef.event._
import org.totalgrid.reef.event.EventType.eventTypeToString
import org.totalgrid.reef.event.SilentEventLogPublisher
import org.totalgrid.reef.services.core.util._

import java.util.{ Date, Calendar }

@RunWith(classOf[JUnitRunner])
class AlarmQueryServiceTest extends FixtureSuite with BeforeAndAfterAll with ShouldMatchers {
  import org.totalgrid.reef.services.ServiceResponseTestingHelpers._
  import Alarm.State._

  val ALARM = EventConfig.Designation.ALARM.getNumber
  val EVENT = EventConfig.Designation.EVENT.getNumber
  val LOG = EventConfig.Designation.LOG.getNumber

  val STATE_UNACK = List(UNACK_AUDIBLE, UNACK_AUDIBLE)
  val STATE_ACK = List(ACKNOWLEDGED)
  val STATE_REM = List(REMOVED)
  val STATE_ANY = List[Alarm.State]()

  // Let's use some values that are inaccurate, but easy to debug!
  val DAYS_AGO_2 = 10000L
  val DAYS_AGO_1 = 20000L
  val HOURS_AGO_2 = 30000L
  val HOURS_AGO_1 = 40000L
  val NOW = 50000L

  val CRITICAL = 1
  val INFORM = 6

  val USER_ANY = "*"
  val USER1 = "user1"
  val USER2 = "user2"
  val USER3 = "user3"

  // UIDs are strings in protos and long in the DB.
  var ENTITY1 = "Entity1"
  var ENTITY2 = "Entity2"
  var ENTITY_ANY = ""

  val SUB1 = "subsystem1"

  /**
   *  Do this once, then run all tests.
   */
  override def beforeAll() {
    DbConnector.connect(DbInfo.loadInfo("test"))
    transaction { ApplicationSchema.reset }
    val al = new AlarmAndEventInserter
    // All our tests are gets, so seed the table once for all tests.
    al.seedEventConfigTable
    al.seedMessages
  }

  class AlarmAndEventInserter {
    // Create the service factories
    //

    def seedEventConfigTable() {
      import org.squeryl.PrimitiveTypeMode._
      import org.squeryl.Table
      import org.totalgrid.reef.models.{ ApplicationSchema, EventConfigStore }
      import EventType._

      val ecs = List[EventConfigStore](
        //               EventType    SEVERITY  DESIGNATION ALARM_STATE RESOURCE
        EventConfigStore(System.UserLogin, 7, EVENT, 0, "User logged in"),
        EventConfigStore(System.UserLogout, 7, EVENT, 0, "User logged out"),
        EventConfigStore(System.SubsystemStarting, 8, LOG, 0, "Subsystem is starting"),
        EventConfigStore(System.SubsystemStarted, 8, LOG, 0, "Subsystem has started"),
        EventConfigStore(System.SubsystemStopping, 8, LOG, 0, "Subsystem is stapping"),
        EventConfigStore(System.SubsystemStopped, 8, LOG, 0, "Subsystem has stopped"),
        EventConfigStore(Scada.ControlExe, 3, ALARM, AlarmModel.UNACK_AUDIBLE, "User executed control {attr0} on device {attr1}"))

      transaction {
        ecs.foreach(ApplicationSchema.eventConfigs.insert(_))
      }
    }

    def seedMessages() {
      import org.squeryl.PrimitiveTypeMode._
      import org.squeryl.Table
      import org.totalgrid.reef.models.{ ApplicationSchema, EventStore }
      import EventType._

      transaction {

        // Post events to create alarms, events, and logs 

        val entity1 = ApplicationSchema.entities.insert(new Entity(ENTITY1))
        val entity2 = ApplicationSchema.entities.insert(new Entity(ENTITY2))

        val factories = new ModelFactories(new SilentEventPublishers, new SilentSummaryPoints)

        val eventService = factories.events.model

        eventService.createFromProto(makeEvent(System.UserLogin, DAYS_AGO_2, USER1, None))
        eventService.createFromProto(makeEvent(Scada.ControlExe, DAYS_AGO_2 + 1000, USER1, Some(entity1.id.toString)))

        eventService.createFromProto(makeEvent(System.UserLogin, HOURS_AGO_2, USER2, None))
        eventService.createFromProto(makeEvent(Scada.ControlExe, HOURS_AGO_2 + 1000, USER2, Some(entity2.id.toString)))
        eventService.createFromProto(makeEvent(System.UserLogout, HOURS_AGO_2 + 2000, USER2, None))

        eventService.createFromProto(makeEvent(System.UserLogin, HOURS_AGO_1, USER3, None))
        eventService.createFromProto(makeEvent(Scada.ControlExe, HOURS_AGO_1 + 1000, USER3, Some(entity2.id.toString)))
        eventService.createFromProto(makeEvent(System.UserLogout, HOURS_AGO_1 + 2000, USER3, None))

        eventService.createFromProto(makeEvent(System.UserLogout, NOW, USER1, None))

      }
    }
  }

  case class Fixture(service: AlarmQueryService)
  type FixtureParam = Fixture

  /**
   *  This is run before each test.
   */
  def withFixture(test: OneArgTest) = {
    import EventType._

    val pubs = new SilentEventPublishers
    val fac = new AlarmServiceModelFactory(pubs, new SilentSummaryPoints)
    val service = new AlarmQueryService

    test(Fixture(service))
  }

  def testFailPutAlarmList(fixture: Fixture) {
    import fixture._
    import EventType._

    val resp = service.put(makeAL(STATE_ANY, 0, 0, Some(Scada.ControlExe), USER_ANY, ENTITY_ANY))
    resp.status should equal(Envelope.Status.NOT_ALLOWED)
  }

  def testSimpleQueries(fixture: Fixture) {
    import fixture._
    import EventType._

    // Select EventType only.
    //

    var resp = one(service.get(makeAL(STATE_ANY, 0, 0, Some(Scada.ControlExe), USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(3)
    resp.getAlarmsList.toIterable.foreach(a => a.getEvent.getEventType should equal(Scada.ControlExe.toString))

    resp = one(service.get(makeAL(STATE_ANY, 0, 0, Some(System.UserLogin), USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(0)

    // Select EventType and user
    //

    resp = one(service.get(makeAL(STATE_ANY, 0, 0, Some(System.UserLogout), USER1, ENTITY_ANY)))
    resp.getAlarmsCount should equal(0)

    resp = one(service.get(makeAL(STATE_ANY, 0, 0, None, USER1, ENTITY_ANY)))
    resp.getAlarmsCount should equal(1)

    // Select EventType, user, and entity
    //

    resp = one(service.get(makeAL(STATE_ANY, 0, 0, Some(Scada.ControlExe), USER1, ENTITY1)))
    resp.getAlarmsCount should equal(1)
    resp.getAlarms(0).getEvent.getEventType should equal(Scada.ControlExe.toString)
    resp.getAlarms(0).getEvent.getUserId should equal(USER1)
    resp.getAlarms(0).getEvent.getEntity.getName should equal(ENTITY1)

  }
  /*
  def testQueriesWithSets(fixture: Fixture) {
    import fixture._
    import EventType._

    val empty = List[String]()
    val anyEventType = List[EventType]()

    var resp = one(service.get(makeAL(0, 0, List(Scada.ControlExe, System.UserLogin), empty, empty)))
    resp.getAlarmsCount should equal(6)

    resp = one(service.get(makeAL(0, 0, anyEventType, List(USER1, USER2), empty)))
    resp.getAlarmsCount should equal(6)

    resp = one(service.get(makeAL(0, 0, anyEventType, List(USER1, USER2, USER3), empty)))
    resp.getAlarmsCount should equal(9)

    resp = one(service.get(makeAL(0, 0, anyEventType, empty, List(ENTITY1, ENTITY2))))
    resp.getAlarmsCount should equal(3)

    resp = one(service.get(makeAL(0, 0, List(System.UserLogin, System.UserLogout), List(USER1, USER2, USER3), empty)))
    resp.getAlarmsCount should equal(6)

  }

  def testQueriesWithTime(fixture: Fixture) {
    import fixture._
    import EventType._

    var resp = one(service.get(makeAL(0, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(9)

    resp = one(service.get(makeAL(HOURS_AGO_1, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(4)
    //resp.getAlarmList.toIterable.foreach(e => e.getResourceId should equal(ENTITY2))

    resp = one(service.get(makeAL(HOURS_AGO_1, 0, System.UserLogout, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(2)
    resp.getAlarmList.toIterable.foreach(e => e.getEventType should equal(System.UserLogout.toString))

    resp = one(service.get(makeAL(DAYS_AGO_2, HOURS_AGO_2 + 9000, None, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(5)

    resp = one(service.get(makeAL(DAYS_AGO_2, HOURS_AGO_1 + 9000, None, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(8)

    // Just timeFrom
    resp = one(service.get(makeAL(DAYS_AGO_2, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(9)
    resp = one(service.get(makeAL(HOURS_AGO_2, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(7)

  }
*/
  /**
   *  Add some events to the database, and see if we're getting the updates.
   */
  /*
  def testUpdates(fixture: Fixture) {
    import fixture._
    import EventType._
    import org.squeryl.PrimitiveTypeMode._
    import org.squeryl.Table
    import org.totalgrid.reef.models.{ ApplicationSchema, EventStore }
    import EventType._

    val ENTITY42 = "42" // Make the entity for updated entries unique.

    var resp = one(service.get(makeAL(0, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getAlarmsCount should equal(9)
    var lastUid = resp.getAlarmList.head.getUid // The latest UID in the database

    val events = List[EventStore](
      // EventStore: EventType, alarm, time, deviceTime, severity, subsystem, userId, entityUid, args

      // Overlap the first event with the same time as the last event to make sure the don't get overlaps
      // and we don't miss one.
      //
      EventStore(System.UserLogin, false, NOW, 0, INFORM, SUB1, USER1, ENTITY42.toLong, Array[Byte]()),
      EventStore(Scada.ControlExe, false, NOW + 1, 0, CRITICAL, SUB1, USER1, ENTITY42.toLong, Array[Byte]()),

      EventStore(System.UserLogin, false, NOW + 2, 0, INFORM, SUB1, USER2, ENTITY42.toLong, Array[Byte]()),
      EventStore(Scada.ControlExe, false, NOW + 3, 0, CRITICAL, SUB1, USER2, ENTITY42.toLong, Array[Byte]()),
      EventStore(System.UserLogout, false, NOW + 4, 0, INFORM, SUB1, USER2, ENTITY42.toLong, Array[Byte]()),

      EventStore(System.UserLogin, false, NOW + 5, 0, INFORM, SUB1, USER3, ENTITY42.toLong, Array[Byte]()),
      EventStore(Scada.ControlExe, false, NOW + 6, 0, CRITICAL, SUB1, USER3, ENTITY42.toLong, Array[Byte]()),
      EventStore(System.UserLogout, false, NOW + 7, 0, INFORM, SUB1, USER3, ENTITY42.toLong, Array[Byte]()),

      EventStore(System.UserLogout, false, NOW + 8, 0, INFORM, SUB1, USER1, ENTITY42.toLong, Array[Byte]()))

    transaction {
      events.foreach(ApplicationSchema.events.insert(_))
    }

    var resp2 = one(service.get(makeAL_UidAfter(STATE_ANY, lastUid, USER_ANY)))
    resp2.getAlarmsCount should equal(9)
    resp2.getAlarmList.toIterable.foreach(e => {
      e.getTime should be >= (NOW)
      e.getEntity.getUid should equal(ENTITY42)
    })

    resp2 = one(service.get(makeAL_UidAfter(STATE_ANY, lastUid, USER1)))
    resp2.getAlarmsCount should equal(3)
    resp2.getAlarmList.toIterable.foreach(e => {
      e.getTime should be >= (NOW)
      e.getEntity.getUid should equal(ENTITY42)
      e.getUserId should equal(USER1)
    })

  }
*/
  ////////////////////////////////////////////////////////
  // Utilities

  /**
   * Make an Event
   */
  def makeEvent(event: EventType, time: Long, userId: String, entityId: Option[String]) = {
    val alist = new AttributeList
    alist += ("attr0" -> AttributeString("val0"))
    alist += ("attr1" -> AttributeString("val1"))

    val b = Event.newBuilder
      .setTime(time)
      .setDeviceTime(0)
      .setEventType(event)
      .setSubsystem("FEP")
      .setUserId(userId)
      .setArgs(alist.toProto)
    entityId.foreach(x => b.setEntity(EntityProto.newBuilder.setUid(x).build))
    b.build
  }

  /**
   * Make an AlarmList proto for selecting events via single parameters
   */
  def makeAL(states: List[Alarm.State], timeFrom: Long, timeTo: Long, eventType: Option[EventType], userId: String, entityName: String) = {

    val es = EventSelect.newBuilder
    if (timeFrom > 0)
      es.setTimeFrom(timeFrom)
    if (timeTo > 0)
      es.setTimeTo(timeTo)
    eventType.foreach(es.addEventType(_))
    if (userId != "") es.addUserId(userId)
    if (entityName != ENTITY_ANY) es.addEntity(EntityProto.newBuilder.setName(entityName).build)

    val as = AlarmSelect.newBuilder
    states.foreach(as.addState)
    as.setEventSelect(es)

    AlarmList.newBuilder
      .setSelect(as)
      .build
  }

  /**
   * Make an AlarmList proto for selecting events via parameter lists
   */
  def makeAL(states: List[Alarm.State], timeFrom: Long, timeTo: Long, eventType: List[EventType], userId: List[String], entityNames: List[String]) = {

    val es = EventSelect.newBuilder
    if (timeFrom > 0)
      es.setTimeFrom(timeFrom)
    if (timeTo > 0)
      es.setTimeTo(timeTo)
    eventType.foreach(x => es.addEventType(x.toString))
    userId.foreach(es.addUserId)
    entityNames.foreach(x => es.addEntity(EntityProto.newBuilder.setName(x).build))

    val as = AlarmSelect.newBuilder
    states.foreach(as.addState)
    as.setEventSelect(es)

    AlarmList.newBuilder
      .setSelect(as)
      .build
  }

  /**
   * Make an AlarmList proto for selecting events after the specified UID.
   */
  def makeAL_UidAfter(states: List[Alarm.State], uid: String, userId: String) = {

    val es = EventSelect.newBuilder
    es.setUidAfter(uid)
    if (userId != "") es.addUserId(userId)

    val as = AlarmSelect.newBuilder
    states.foreach(as.addState)
    as.setEventSelect(es)

    AlarmList.newBuilder
      .setSelect(as)
      .build
  }
}
