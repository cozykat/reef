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

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.totalgrid.reef.client.service.proto.Auth._

import scala.collection.JavaConversions._
import org.totalgrid.reef.services.ServiceResponseTestingHelpers._
import org.totalgrid.reef.client.sapi.client.Expectations._
import org.totalgrid.reef.client.exception.BadRequestException

import org.totalgrid.reef.client.proto.Envelope.SubscriptionEventType._
import org.totalgrid.reef.client.service.proto.Model.{ Entity, ReefUUID }

@RunWith(classOf[JUnitRunner])
class PermissionSetServiceTest extends AuthSystemTestBase {

  case class VerbResource(verb: String, resource: String)

  def makePermissionSet(name: String = "set", expirationTime: Option[Long] = None, allowedPermissions: List[VerbResource] = List(VerbResource("*", "*")), deniedPermissions: List[VerbResource] = Nil) = {
    val b = PermissionSet.newBuilder.setName(name)
    expirationTime.foreach(p => b.setDefaultExpirationTime(p))
    allowedPermissions.foreach(n => b.addPermissions(Permission.newBuilder.setAllow(true).addResource(n.resource).addVerb(n.verb)))
    deniedPermissions.foreach(n => b.addPermissions(Permission.newBuilder.setAllow(false).addResource(n.resource).addVerb(n.verb)))
    b.build
  }

  test("PermissionSet needs name and permission to be created") {
    val fix = new Fixture

    val goodRequest = makePermissionSet()

    intercept[BadRequestException] {
      fix.permissionSetService.put(goodRequest.toBuilder.clearName.build)
    }
    intercept[BadRequestException] {
      fix.permissionSetService.put(goodRequest.toBuilder.clearPermissions.build)
    }
  }

  test("PermissionSet create, update and delete") {
    val fix = new Fixture

    val permissionSet1 = fix.permissionSetService.put(makePermissionSet()).expectOne()
    permissionSet1.getPermissionsCount should equal(1)
    permissionSet1.getDefaultExpirationTime should not equal (10000)

    val permissionSet2 = fix.permissionSetService.put(makePermissionSet(expirationTime = Some(10000))).expectOne()
    permissionSet2.getDefaultExpirationTime should equal(10000)

    val permissionSet3 = fix.permissionSetService.put(makePermissionSet(deniedPermissions = List(VerbResource("*", "*")))).expectOne()
    permissionSet3.getPermissionsCount should equal(2)

    fix.permissionSetService.delete(makePermissionSet()).expectOne()

    val eventList = List(
      (ADDED, classOf[Entity]),
      (ADDED, classOf[PermissionSet]),
      (MODIFIED, classOf[PermissionSet]),
      (MODIFIED, classOf[PermissionSet]),
      (REMOVED, classOf[PermissionSet]),
      (REMOVED, classOf[Entity]))

    fix.eventCheck should equal(eventList)
  }

  test("PermissionSet View and Cleanup") {
    val fix = new Fixture

    val nameWildcard = PermissionSet.newBuilder.setName("*").build
    val uuidWildcard = PermissionSet.newBuilder.setUuid(ReefUUID.newBuilder.setValue("*")).build

    fix.permissionSetService.delete(makePermissionSet("*")).expectMany()
    fix.permissionSetService.get(nameWildcard).expectNone()

    fix.permissionSetService.put(makePermissionSet("all")).expectOne()
    fix.permissionSetService.put(makePermissionSet("read_only")).expectOne()
    fix.permissionSetService.put(makePermissionSet("set3")).expectOne()

    fix.permissionSetService.get(nameWildcard).expectMany(3)
    fix.permissionSetService.get(uuidWildcard).expectMany(3)

    fix.permissionSetService.delete(makePermissionSet("all")).expectOne()
    fix.permissionSetService.delete(makePermissionSet("read_only")).expectOne()
    fix.permissionSetService.delete(makePermissionSet("set3")).expectOne()

    fix.permissionSetService.get(nameWildcard).expectNone()
    fix.permissionSetService.get(uuidWildcard).expectNone()
  }
}