/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.util

import java.io.ByteArrayInputStream
import javax.xml.bind.{ JAXBContext, Marshaller }
import java.io.{ FileWriter, FileReader, StringWriter }

object XMLHelper {

  def read[A](text: String, klass: Class[A]): A = read(text.getBytes("UTF-8"), klass)

  def read[A](bytes: Array[Byte], klass: Class[A]): A = {
    val ctx = JAXBContext.newInstance(klass)
    val um = ctx.createUnmarshaller
    val is = new ByteArrayInputStream(bytes)
    um.unmarshal(is).asInstanceOf[A]
  }

  def read[A](reader: FileReader, klass: Class[A]): A = {
    val ctx = JAXBContext.newInstance(klass)
    val um = ctx.createUnmarshaller
    um.unmarshal(reader).asInstanceOf[A]
  }

  def writeToFile[A](value: A, klass: Class[A], filewriter: FileWriter): Unit = {
    val ctx = JAXBContext.newInstance(klass)
    val m = ctx.createMarshaller
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

    m.marshal(value, filewriter)
  }

  def writeToString[A](value: A, klass: Class[A]): String = {
    val ctx = JAXBContext.newInstance(klass)
    val m = ctx.createMarshaller
    val sw = new StringWriter
    m.marshal(value, sw)
    sw.toString
  }

}
