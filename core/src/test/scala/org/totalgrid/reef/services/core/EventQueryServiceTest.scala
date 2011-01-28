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
import org.totalgrid.reef.proto.Model.{ Entity => EntityProto }
import org.totalgrid.reef.proto.Envelope

import org.scalatest.{ FunSuite, BeforeAndAfterAll }
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.fixture.FixtureSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import scala.collection.JavaConversions._ // proto list to scala list

import org.squeryl.{ Schema, Table, KeyedEntity }
import org.squeryl.PrimitiveTypeMode._

import org.totalgrid.reef.models._
import org.totalgrid.reef.event._
import org.totalgrid.reef.event.EventType.eventTypeToString
import org.totalgrid.reef.event.SilentEventLogPublisher
import org.totalgrid.reef.services.SilentEventPublishers

import java.util.{ Date, Calendar }

@RunWith(classOf[JUnitRunner])
class EventQueryServiceTest extends FixtureSuite with BeforeAndAfterAll with ShouldMatchers {
  import org.totalgrid.reef.services.ServiceResponseTestingHelpers._

  // Need some well known times in the past... all based on NOW
  //  val NOW = now()
  //  val DAYS_AGO_1 = nowPlus( Calendar.DATE, -1)
  //  val DAYS_AGO_2 = nowPlus( Calendar.DATE, -2)
  //  val HOURS_AGO_1 = nowPlus( Calendar.HOUR, -1)
  //  val HOURS_AGO_2 = nowPlus( Calendar.HOUR, -2)

  // Let's use some values that are inaccurate, but easy to debug!
  val DAYS_AGO_2 = 10000L
  val DAYS_AGO_1 = 20000L
  val HOURS_AGO_2 = 30000L
  val HOURS_AGO_1 = 40000L
  val NOW = 50000L

  val CRITICAL = 1
  val INFORM = 6

  val USER_ANY = ""
  val USER1 = "user1"
  val USER2 = "user2"
  val USER3 = "user3"

  // UIDs are strings in protos and long in the DB.
  val ENTITY1 = "Entity1"
  val ENTITY2 = "Entity2"
  val ENTITY_ANY = "" // for proto selects

  val SUB1 = "subsystem1"

  /**
   *  Do this once, then run all tests.
   */
  override def beforeAll() {
    import org.totalgrid.reef.persistence.squeryl.{ DbConnector, DbInfo }
    DbConnector.connect(DbInfo.loadInfo("test"))
    transaction { ApplicationSchema.reset }
    // All our tests are gets, so seed the table once for all tests.
    seedEventTable
  }

  def seedEventTable() {
    import org.squeryl.PrimitiveTypeMode._
    import org.squeryl.Table
    import org.totalgrid.reef.models.{ ApplicationSchema, EventStore }
    import EventType._
    transaction {
      val entity1 = ApplicationSchema.entities.insert(new Entity(ENTITY1))
      val entity2 = ApplicationSchema.entities.insert(new Entity(ENTITY2))

      val events = List[EventStore](
        // EventStore: EventType, alarm, time, deviceTime, severity, subsystem, userId, entityUid, args

        EventStore(System.UserLogin, false, DAYS_AGO_2, 0, INFORM, SUB1, USER1, None, Array[Byte](), ""),
        EventStore(Scada.ControlExe, false, DAYS_AGO_2 + 1000, 0, CRITICAL, SUB1, USER1, Some(entity1.id), Array[Byte](), ""),

        EventStore(System.UserLogin, false, HOURS_AGO_2, 0, INFORM, SUB1, USER2, None, Array[Byte](), ""),
        EventStore(Scada.ControlExe, false, HOURS_AGO_2 + 1000, 0, CRITICAL, SUB1, USER2, Some(entity2.id), Array[Byte](), ""),
        EventStore(System.UserLogout, false, HOURS_AGO_2 + 2000, 0, INFORM, SUB1, USER2, None, Array[Byte](), ""),

        EventStore(System.UserLogin, false, HOURS_AGO_1, 0, INFORM, SUB1, USER3, None, Array[Byte](), ""),
        EventStore(Scada.ControlExe, false, HOURS_AGO_1 + 1000, 0, CRITICAL, SUB1, USER3, Some(entity2.id), Array[Byte](), ""),
        EventStore(System.UserLogout, false, HOURS_AGO_1 + 2000, 0, INFORM, SUB1, USER3, None, Array[Byte](), ""),

        EventStore(System.UserLogout, false, NOW, 0, INFORM, SUB1, USER1, None, Array[Byte](), ""))

      events.foreach(ApplicationSchema.events.insert(_))
    }

  }

  // Get a time offset based on the well known NOW_MS
  def now(): Long = {
    val cal = Calendar.getInstance()
    cal.set(Calendar.MILLISECOND, 0) // truncate milliseconds to 0.
    cal.getTimeInMillis
  }

  // Get a time offset based on the well known NOW_MS
  def nowPlus(field: Int, amount: Int): Long = {
    val cal = Calendar.getInstance
    cal.setTimeInMillis(NOW)
    cal.add(field, amount)
    cal.getTimeInMillis
  }

  case class Fixture(service: EventQueryService)
  type FixtureParam = Fixture

  /**
   *  This is run before each test.
   */
  def withFixture(test: OneArgTest) = {
    import EventType._

    val pubs = new SilentEventPublishers
    val alarms = new AlarmServiceModelFactory(pubs, new SilentSummaryPoints)
    val eventConfig = new EventConfigServiceModelFactory(pubs)
    val fac = new EventServiceModelFactory(pubs, eventConfig, alarms)
    val service = new EventQueryService(fac)

    test(Fixture(service))
  }

  def testFailPutEventList(fixture: Fixture) {
    import org.totalgrid.reef.messaging.ProtoServiceException
    import fixture._
    import EventType._

    val resp = service.put(makeEL(0, 0, Some(Scada.ControlExe), USER_ANY, ENTITY_ANY))
    resp.status should equal(Envelope.Status.NOT_ALLOWED)
    //intercept[ProtoServiceException] {
    //  val resp = service.put(makeEL(0, 0, Scada.ControlExe, USER_ANY, ENTITY_ANY))
    //}
  }

  def testSimpleQueries(fixture: Fixture) {
    import fixture._
    import EventType._

    // Select EventType only.
    //

    var resp = one(service.get(makeEL(0, 0, Some(Scada.ControlExe), USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(3)
    resp.getEventsList.toIterable.foreach(e => e.getEventType should equal(Scada.ControlExe.toString))

    resp = one(service.get(makeEL(0, 0, Some(System.UserLogin), USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(3)
    resp.getEventsList.toIterable.foreach(e => e.getEventType should equal(System.UserLogin.toString))

    // Select EventType and user
    //

    resp = one(service.get(makeEL(0, 0, Some(System.UserLogout), USER1, ENTITY_ANY)))
    resp.getEventsCount should equal(1)
    resp.getEvents(0).getEventType should equal(System.UserLogout.toString)

    resp = one(service.get(makeEL(0, 0, None, USER1, ENTITY_ANY)))
    resp.getEventsCount should equal(3)
    resp.getEventsList.toIterable.foreach(e => e.getUserId should equal(USER1))

    // Select EventType and entity
    //

    resp = one(service.get(makeEL(0, 0, None, "", ENTITY2)))
    resp.getEventsCount should equal(2)
    resp.getEventsList.toIterable.foreach(e => e.getEntity.getName should equal(ENTITY2))

    // Select EventType, user, and entity
    //

    resp = one(service.get(makeEL(0, 0, Some(Scada.ControlExe), USER1, ENTITY1)))
    resp.getEventsCount should equal(1)
    resp.getEvents(0).getEventType should equal(Scada.ControlExe.toString)
    resp.getEvents(0).getUserId should equal(USER1)
    resp.getEvents(0).getEntity.getName should equal(ENTITY1)

  }

  def testQueriesWithSets(fixture: Fixture) {
    import fixture._
    import EventType._

    val empty = List[String]()
    val anyEventType = List[EventType]()

    var resp = one(service.get(makeEL(0, 0, List(Scada.ControlExe, System.UserLogin), empty, empty)))
    resp.getEventsCount should equal(6)

    resp = one(service.get(makeEL(0, 0, anyEventType, List(USER1, USER2), empty)))
    resp.getEventsCount should equal(6)

    resp = one(service.get(makeEL(0, 0, anyEventType, List(USER1, USER2, USER3), empty)))
    resp.getEventsCount should equal(9)

    resp = one(service.get(makeEL(0, 0, anyEventType, empty, List(ENTITY1, ENTITY2))))
    resp.getEventsCount should equal(3)

    resp = one(service.get(makeEL(0, 0, List(System.UserLogin, System.UserLogout), List(USER1, USER2, USER3), empty)))
    resp.getEventsCount should equal(6)

  }

  def testQueriesWithTime(fixture: Fixture) {
    import fixture._
    import EventType._

    var resp = one(service.get(makeEL(0, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(9)

    resp = one(service.get(makeEL(HOURS_AGO_1, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(4)
    //resp.getEventsList.toIterable.foreach(e => e.getResourceId should equal(ENTITY2))

    resp = one(service.get(makeEL(HOURS_AGO_1, 0, Some(System.UserLogout), USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(2)
    resp.getEventsList.toIterable.foreach(e => e.getEventType should equal(System.UserLogout.toString))

    resp = one(service.get(makeEL(DAYS_AGO_2, HOURS_AGO_2 + 9000, None, USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(5)

    resp = one(service.get(makeEL(DAYS_AGO_2, HOURS_AGO_1 + 9000, None, USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(8)

    // Just timeFrom
    resp = one(service.get(makeEL(DAYS_AGO_2, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(9)
    resp = one(service.get(makeEL(HOURS_AGO_2, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(7)

  }

  /**
   *  Add some events to the database, and see if we're getting the updates.
   */
  def testUpdates(fixture: Fixture) {
    import fixture._
    import EventType._
    import org.squeryl.PrimitiveTypeMode._
    import org.squeryl.Table
    import org.totalgrid.reef.models.{ ApplicationSchema, EventStore }
    import EventType._

    val ENTITY42 = "Entity42" // Make the entity for updated entries unique.

    var resp = one(service.get(makeEL(0, 0, None, USER_ANY, ENTITY_ANY)))
    resp.getEventsCount should equal(9)
    var lastUid = resp.getEventsList.head.getUid // The latest UID in the database

    val entity = transaction {
      ApplicationSchema.entities.insert(new Entity("Entity42"))
    }

    val events = List[EventStore](
      // EventStore: EventType, alarm, time, deviceTime, severity, subsystem, userId, entityUid, args

      // Overlap the first event with the same time as the last event to make sure the don't get overlaps
      // and we don't miss one.
      //
      EventStore(System.UserLogin, false, NOW, 0, INFORM, SUB1, USER1, Some(entity.id), Array[Byte](), ""),
      EventStore(Scada.ControlExe, false, NOW + 1, 0, CRITICAL, SUB1, USER1, Some(entity.id), Array[Byte](), ""),

      EventStore(System.UserLogin, false, NOW + 2, 0, INFORM, SUB1, USER2, Some(entity.id), Array[Byte](), ""),
      EventStore(Scada.ControlExe, false, NOW + 3, 0, CRITICAL, SUB1, USER2, Some(entity.id), Array[Byte](), ""),
      EventStore(System.UserLogout, false, NOW + 4, 0, INFORM, SUB1, USER2, Some(entity.id), Array[Byte](), ""),

      EventStore(System.UserLogin, false, NOW + 5, 0, INFORM, SUB1, USER3, Some(entity.id), Array[Byte](), ""),
      EventStore(Scada.ControlExe, false, NOW + 6, 0, CRITICAL, SUB1, USER3, Some(entity.id), Array[Byte](), ""),
      EventStore(System.UserLogout, false, NOW + 7, 0, INFORM, SUB1, USER3, Some(entity.id), Array[Byte](), ""),

      EventStore(System.UserLogout, false, NOW + 8, 0, INFORM, SUB1, USER1, Some(entity.id), Array[Byte](), ""))

    transaction {
      events.foreach(ApplicationSchema.events.insert(_))
    }

    var resp2 = one(service.get(makeEL_UidAfter(lastUid, USER_ANY)))
    resp2.getEventsCount should equal(9)
    resp2.getEventsList.toIterable.foreach(e => {
      e.getTime should be >= (NOW)
      e.getEntity.getName should equal(ENTITY42)
    })

    resp2 = one(service.get(makeEL_UidAfter(lastUid, USER1)))
    resp2.getEventsCount should equal(3)
    resp2.getEventsList.toIterable.foreach(e => {
      e.getTime should be >= (NOW)
      e.getEntity.getName should equal(ENTITY42)
      e.getUserId should equal(USER1)
    })

  }

  ////////////////////////////////////////////////////////
  // Utilities

  /**
   * Make an EventList proto for selecting events via single parameters
   */
  def makeEL(timeFrom: Long, timeTo: Long, eventType: Option[EventType], userId: String, entityName: String) = {

    val q = EventSelect.newBuilder

    if (timeFrom > 0)
      q.setTimeFrom(timeFrom)
    if (timeTo > 0)
      q.setTimeTo(timeTo)

    eventType.foreach(q.addEventType(_))
    if (userId != "") q.addUserId(userId)
    if (entityName != ENTITY_ANY) q.addEntity(EntityProto.newBuilder.setName(entityName).build)

    EventList.newBuilder
      .setSelect(q)
      .build
  }

  /**
   * Make an EventList proto for selecting events via parameter lists
   */
  def makeEL(timeFrom: Long, timeTo: Long, eventType: List[EventType], userId: List[String], entityNames: List[String]) = {

    val q = EventSelect.newBuilder

    if (timeFrom > 0)
      q.setTimeFrom(timeFrom)
    if (timeTo > 0)
      q.setTimeTo(timeTo)

    eventType.foreach(x => q.addEventType(x.toString))
    userId.foreach(q.addUserId)
    entityNames.foreach(x => q.addEntity(EntityProto.newBuilder.setName(x).build))
    EventList.newBuilder
      .setSelect(q)
      .build
  }

  /**
   * Make an EventList proto for selecting events after the specified UID.
   */
  def makeEL_UidAfter(uid: String, userId: String) = {

    val q = EventSelect.newBuilder

    q.setUidAfter(uid)

    if (userId != "") q.addUserId(userId)

    EventList.newBuilder
      .setSelect(q)
      .build
  }
}
