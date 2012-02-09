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
package org.totalgrid.reef.measurementstore.encoders

import java.util.zip.{ Inflater, Deflater }
import java.io.ByteArrayOutputStream

import org.totalgrid.reef.client.service.proto.Measurements

object JavaZipping {

  def compress(input: Array[Byte]): Array[Byte] = {
    val deflater = new Deflater
    deflater.setLevel(Deflater.BEST_COMPRESSION)
    deflater.setInput(input)
    deflater.finish
    val bos = new ByteArrayOutputStream(input.length)
    val buf = new Array[Byte](1024)

    while (!deflater.finished)
      bos.write(buf, 0, deflater.deflate(buf))

    bos.close
    bos.toByteArray
  }

  def decompress(input: Array[Byte]): Array[Byte] = {
    val inflater = new Inflater
    inflater.setInput(input)
    val bos = new ByteArrayOutputStream(input.length)

    val buf = new Array[Byte](1024)
    while (!inflater.finished)
      bos.write(buf, 0, inflater.inflate(buf))

    bos.close
    bos.toByteArray
  }

}

trait JavaZipping extends MeasEncoder {

  abstract override def encode(meas: Seq[Measurements.Measurement]): Array[Byte] = {
    JavaZipping.compress(super.encode(meas))
  }

  abstract override def decode(serialized: Array[Byte]): Seq[Measurements.Measurement] = {
    super.decode(JavaZipping.decompress(serialized))
  }

}
