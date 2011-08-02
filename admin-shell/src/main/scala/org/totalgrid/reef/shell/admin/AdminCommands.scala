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
package org.totalgrid.reef.shell.admin

import org.apache.felix.gogo.commands.Command
import org.totalgrid.reef.shell.proto.ReefCommandSupport

import org.totalgrid.reef.osgi.OsgiConfigReader
import org.totalgrid.reef.persistence.squeryl.{ DbConnector, SqlProperties }
import org.totalgrid.reef.measurementstore.MeasurementStoreFinder
import org.totalgrid.reef.executor.{ ReactActorExecutor, LifecycleManager }
import org.totalgrid.reef.services.ServiceBootstrap

@Command(scope = "reef", name = "resetdb", description = "Clears and resets sql tables")
class ResetDatabaseCommand extends ReefCommandSupport {

  override val requiresLogin = false

  override def doCommand(): Unit = {
    val sql = SqlProperties.get(new OsgiConfigReader(getBundleContext, "org.totalgrid.reef.sql"))
    logout()

    val bundleContext = getBundleContext()

    DbConnector.connect(sql, bundleContext)

    val exe = new ReactActorExecutor {}

    val mstore = MeasurementStoreFinder.getInstance(sql, exe, bundleContext)

    exe.start()
    try {
      ServiceBootstrap.resetDb()
      ServiceBootstrap.seed()
      println("Cleared and updated jvm database")

      if (mstore.reset) {
        println("Cleared measurement store")
      } else {
        println("NOTE: measurement store not reset, needs to be done manually")
      }
    } catch {
      case ex => println("Reset failed: " + ex.toString)
    }
    finally {
      exe.stop()
    }
  }

}

