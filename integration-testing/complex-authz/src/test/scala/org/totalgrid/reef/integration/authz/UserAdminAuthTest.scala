/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.integration.authz

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UserAdminAuthTest extends AuthTestBase {

  override val modelFile = "../../assemblies/assembly-common/filtered-resources/samples/authorization/config.xml"

  test("User admin can't see live system") {
    as("user_admin") { admin =>
      unAuthed("user_admin shouldn't see system") { admin.getCommands() }
      unAuthed("user_admin shouldn't see system") { admin.getCommandHistory() }
      unAuthed("user_admin shouldn't see system") { admin.getPoints() }
      unAuthed("user_admin shouldn't see system") { admin.getEndpointConnections() }
    }
  }

  test("User admin can create new user") {
    as("user_admin") { admin =>
      val agent = admin.getAgents().filter(_.getName == "fakeUser").headOption
      agent.foreach { admin.deleteAgent(_) }

      val fakeUser = admin.createNewAgent("fakeUser", "system", List("user_role", "system_viewer"))
      try {
        // fake user can change own password
        as("fakeUser") { limitedUser =>
          limitedUser.setAgentPassword("fakeUser", "password")

          unAuthed("Cannot update admin user password") {
            limitedUser.setAgentPassword("admin", "password")
          }
        }
        unAuthed("Password should have been changed") {
          as("fakeUser") { _ => }
        }
      } finally {
        admin.deleteAgent(fakeUser)
      }
    }
  }
}