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
package org.totalgrid.reef.protocol.dnp3.mock

import org.totalgrid.reef.util.EmptySyncVar
import org.totalgrid.reef.protocol.dnp3.{ CommandResponse, CommandStatus, IResponseAcceptor }

class CachingResponseAcceptor extends IResponseAcceptor {

  val responsesRecieved = new EmptySyncVar[(Int, CommandStatus)]
  override def AcceptResponse(response: CommandResponse, sequence: Int) {
    responsesRecieved.update((sequence, response.getMResult))
  }

  def waitFor(sequence: Int, status: CommandStatus) = {
    responsesRecieved.waitFor({ r => r._1 == sequence && r._2 == status })
  }
}