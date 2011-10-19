package org.totalgrid.reef.api.sapi.client.impl

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
import net.agileautomata.executor4s.{ Future, Result }
import org.totalgrid.reef.api.sapi.client.Promise

class WrappedFuturePromise[A](future: Future[Result[A]]) extends Promise[A] {

  def await: A = future.await.get
  def listen(fun: Promise[A] => Unit) = {
    future.listen(result => fun(this))
    this
  }
  def extract: Result[A] = future.await
  def map[B](fun: A => B) = Promise.from(future.map(_.map(fun)))
  def isComplete: Boolean = future.isComplete

}



