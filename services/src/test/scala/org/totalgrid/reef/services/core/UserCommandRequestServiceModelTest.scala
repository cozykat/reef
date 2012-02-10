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

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.squeryl.PrimitiveTypeMode._

import org.totalgrid.reef.client.service.proto.Model.{ Command => CommandProto }
import org.totalgrid.reef.client.service.proto.Commands.{ CommandStatus, CommandRequest, CommandLock }
import org.totalgrid.reef.client.service.proto.Commands.CommandLock._

import org.totalgrid.reef.models._
import org.totalgrid.reef.client.sapi.client.BasicRequestHeaders
import org.totalgrid.reef.client.exception.{ ReefServiceException, BadRequestException }
import org.totalgrid.reef.persistence.squeryl.DbConnection

@RunWith(classOf[JUnitRunner])
class UserCommandRequestServiceModelTest extends DatabaseUsingTestBase with SyncServicesTestHelpers {

  class TestRig(dbConnection: DbConnection) extends CommandTestRig(dbConnection) {

    val cid = seed("cmd01").id
    def cmd = ApplicationSchema.commands.where(c => c.id === cid).single

    def scenario(mode: AccessMode, time: Long, user: String) = {

      val updated = cmd
      val select = seed(new CommandLockModel(mode.getNumber, Some(time), Some(user)))
      ApplicationSchema.commandToBlocks.insert(new CommandBlockJoin(updated.id, select.id))

      updated.lastSelectId = Some(select.id)
      ApplicationSchema.commands.update(updated)
      select
    }

  }

  private def cmdReq = CommandRequest.newBuilder.setCommand(CommandProto.newBuilder.setName("cmd01")).build

  val userName = "user"

  val context = defaultContextSource.getContext

  private def insert(r: TestRig, time: Long) = {
    r.userRequests.table.insert(new UserCommandModel(r.cmd.id, "", userName, CommandStatus.EXECUTING.getNumber, time, cmdReq.toByteString.toByteArray, None))
  }

  def markCompleted(status: CommandStatus) {
    val r = new TestRig(dbConnection)

    val inserted = insert(r, 5000 + System.currentTimeMillis)

    r.userRequests.markCompleted(context, inserted, status)

    val entries = ApplicationSchema.userRequests.where(t => true === true).toList
    entries.length should equal(1)
    val entry = entries.head
    entry.commandId should equal(r.cmd.id)
    entry.agent should equal(userName)
    entry.status should equal(status.getNumber)
  }

  test("Mark completed success") {
    markCompleted(CommandStatus.SUCCESS)
  }
  test("Mark completed error") {
    markCompleted(CommandStatus.HARDWARE_ERROR)
  }

  test("Mark expired") {
    val r = new TestRig(dbConnection)

    val inserted = insert(r, System.currentTimeMillis - 5000)

    r.userRequests.findAndMarkExpired(context)

    val entries = ApplicationSchema.userRequests.where(t => true === true).toList
    entries.length should equal(1)
    val entry = entries.head
    entry.commandId should equal(r.cmd.id)
    entry.agent should equal(userName)
    entry.status should equal(CommandStatus.TIMEOUT.getNumber)
  }

  def failScenario(mode: AccessMode, time: Long, user: String) {
    val r = new TestRig(dbConnection)
    r.scenario(mode, time, user)

    intercept[ReefServiceException] {
      r.userRequests.issueCommand(context, "cmd01", "", userName, 5000, cmdReq)
    }
  }

  test("Request") {
    val r = new TestRig(dbConnection)
    val time = System.currentTimeMillis + 40000
    r.scenario(AccessMode.ALLOWED, time, userName)

    r.userRequests.issueCommand(context, "cmd01", "", userName, 5000, cmdReq)

    val entries = ApplicationSchema.userRequests.where(t => true === true).toList
    entries.length should equal(1)
    val entry = entries.head
    entry.commandId should equal(r.cmd.id)
    entry.agent should equal(userName)
    entry.status should equal(CommandStatus.EXECUTING.getNumber)
  }

  test("Fail, blocked") {
    failScenario(AccessMode.BLOCKED, System.currentTimeMillis + 40000, userName)
  }

  test("Fail, expired") {
    failScenario(AccessMode.ALLOWED, System.currentTimeMillis - 40000, userName)
  }

  test("Fail, wrong user") {
    failScenario(AccessMode.ALLOWED, System.currentTimeMillis + 40000, "user02")
  }

  test("Cannot delete command with outstanding select") {
    val r = new TestRig(dbConnection)
    val time = System.currentTimeMillis + 40000
    val select = r.scenario(AccessMode.ALLOWED, time, userName)

    intercept[BadRequestException] {
      r.commands.delete(context, r.cmd)
    }

    r.accesses.removeAccess(context, select)

    r.commands.delete(context, r.cmd)
  }

  test("Can delete command with expired select") {
    val r = new TestRig(dbConnection)
    val time = System.currentTimeMillis - 40000
    val select = r.scenario(AccessMode.ALLOWED, time, userName)

    r.commands.delete(context, r.cmd)
  }

  test("Deleting command removes history and select") {
    val r = new TestRig(dbConnection)
    val now = System.currentTimeMillis

    val select1 = r.scenario(AccessMode.ALLOWED, now - 40000, userName)
    val select2 = r.scenario(AccessMode.ALLOWED, now - 20000, userName)
    val select3 = r.scenario(AccessMode.ALLOWED, now + 40000, userName)

    r.userRequests.issueCommand(context, "cmd01", "", userName, 5000, cmdReq)
    r.userRequests.issueCommand(context, "cmd01", "", userName, 5000, cmdReq)
    r.userRequests.issueCommand(context, "cmd01", "", userName, 5000, cmdReq)
    r.accesses.removeAccess(context, select3)

    val requests = ApplicationSchema.userRequests.where(t => true === true).toList
    requests.length should equal(3)

    val selects = ApplicationSchema.commandAccess.where(t => true === true).toList
    selects.length should equal(2)

    r.commands.delete(context, r.cmd)

    ApplicationSchema.userRequests.where(t => true === true).toList.size should equal(0)
    ApplicationSchema.commandAccess.where(t => true === true).toList.size should equal(0)
  }
}