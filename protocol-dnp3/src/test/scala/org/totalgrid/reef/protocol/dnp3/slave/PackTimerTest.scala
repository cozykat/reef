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
package org.totalgrid.reef.api.protocol.dnp3.slave

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.totalgrid.reef.executor.mock.MockExecutor

@RunWith(classOf[JUnitRunner])
class PackTimerTest extends FunSuite with ShouldMatchers {

  case class TestObject(i: Int)

  test("Publishing on delay") {

    var pubbed = List.empty[List[TestObject]]
    val pubFunc = (l: List[TestObject]) => pubbed ::= l
    val exe = new MockExecutor

    val packTimer = new PackTimer(10, 10, pubFunc, exe)
    exe.numActionsPending should equal(0)

    packTimer.addEntry(TestObject(0))
    exe.numActionsPending should equal(1)

    packTimer.addEntry(TestObject(1))

    exe.numActionsPending should equal(1)
    exe.delayNext(1, 0) should equal(10)
    exe.numActionsPending should equal(0)

    pubbed.head should equal(List(TestObject(0), TestObject(1)))
  }

  test("Publishing when full") {

    var pubbed = List.empty[List[TestObject]]
    val pubFunc = (l: List[TestObject]) => pubbed ::= l
    val exe = new MockExecutor

    val packTimer = new PackTimer(10, 10, pubFunc, exe)
    exe.numActionsPending should equal(0)

    (0 to 8).foreach { i =>
      packTimer.addEntry(TestObject(i))
      exe.numActionsPending should equal(1)
    }
    packTimer.addEntry(TestObject(9))
    exe.numActionsPending should equal(2)

    exe.executeNext(2, 0)
    pubbed.head should equal((0 to 9).map { TestObject(_) }.toList)
    exe.numActionsPending should equal(0)

  }
}