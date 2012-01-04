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
package org.totalgrid.reef.client.sapi.rpc.impl

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.totalgrid.reef.client.sapi.rpc.impl.util.ClientSessionSuite
import org.totalgrid.reef.client.proto.Envelope.Verb
import net.agileautomata.executor4s.Future
import org.totalgrid.reef.client.sapi.client.{ Response, RequestSpy }
import org.totalgrid.reef.client.service.entity.EntityRelation

@RunWith(classOf[JUnitRunner])
class RequestSpyTest extends ClientSessionSuite("RequestSpy.xml", "RequestSpy", <div/>) {

  class CountingRequestSpy extends RequestSpy {
    var count = 0
    def onRequestReply[A](verb: Verb, request: A, response: Future[Response[A]]) = count += 1
    def reset() = count = 0
  }

  test("CountingRequestSpy") {
    val spy = new CountingRequestSpy
    client.addRequestSpy(spy)

    val relations = List(new EntityRelation("feedback", "Point", false))

    val fromRoots = client.getEntityRelationsFromTypeRoots("Command", relations).await

    spy.count should equal(1)
    spy.reset()

    val fromParents = client.getEntityRelationsForParents(fromRoots.map { _.getUuid }, relations).await

    fromParents should equal(fromRoots)

    // N queries, one fore each command and another for the batch
    spy.count should equal(fromRoots.size + 1)
  }
}