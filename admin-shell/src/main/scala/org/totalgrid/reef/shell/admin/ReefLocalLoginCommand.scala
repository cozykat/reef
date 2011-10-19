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
import org.apache.felix.gogo.commands.Command._
import org.apache.karaf.shell.console.OsgiCommandSupport._
import org.totalgrid.reef.shell.proto.ReefLoginCommandBase

//@Command(scope = "reef", name = "local-login", description = "Authorizes a user with the local Reef node, asks for password interactively")
//class ReefLocalLoginCommand extends ReefLoginCommandBase {
//
//  def setupReefSession() = (new OsgiClientSession(getBundleContext), "local")
//
//}