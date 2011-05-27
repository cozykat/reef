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
package org.totalgrid.reef.messaging.javaclient

import org.totalgrid.reef.sapi.client.{ Promise => IScalaPromise, Response => ScalaResponse }
import org.totalgrid.reef.japi.client.{ Promise, ResponseListener, Response }

class PromiseWrapper[A](promise: IScalaPromise[ScalaResponse[A]]) extends Promise[Response[A]] {

  private lazy val response = new ResponseWrapper(promise.await())

  final override def await(): Response[A] = response

  final override def addListener(listener: ResponseListener[Response[A]]): Unit = promise.listen { rsp =>
    listener.onCompletion(new ResponseWrapper[A](rsp))
  }

  final override def isComplete = promise.isComplete
}
