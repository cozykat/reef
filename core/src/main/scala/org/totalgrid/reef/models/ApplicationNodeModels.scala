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
package org.totalgrid.reef.models

import org.squeryl.PrimitiveTypeMode._

import org.totalgrid.reef.util.LazyVar

case class ApplicationCapability(
    val applicationId: Long,
    val capability: String) extends ModelWithId {

  val application = LazyVar(hasOne(ApplicationSchema.apps, applicationId))
}

case class ApplicationInstance(
    val instanceName: String,
    val userName: String,
    var location: String,
    var network: String) extends ModelWithId {

  val heartbeat = LazyVar(belongTo(ApplicationSchema.heartbeats.where(p => p.applicationId === id)))

  val capabilities = LazyVar(ApplicationSchema.capabilities.where(p => p.applicationId === id))
}

class HeartbeatStatus(
    val applicationId: Long,
    val periodMS: Int,
    var timeoutAt: Long,
    var isOnline: Boolean,
    val processId: String) extends ModelWithId {

  val application = LazyVar(hasOne(ApplicationSchema.apps, applicationId))

  val instanceName = LazyVar(application.value.instanceName)
}