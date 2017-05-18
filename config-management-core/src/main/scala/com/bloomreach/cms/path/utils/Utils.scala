/**
 * Copyright 2016 BloomReach, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.bloomreach.cms.path.utils

import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.json4s.JsonAST.{JValue, JArray, JObject, JField, JNothing}

object Utils {
  val logger = LoggerFactory.getLogger(this.getClass)
    
  def getKeyNameFromPath(keyPath: String) = keyPath.split("/").last
  
  /**
    * @param key
    * @return full parent key
    */
  def getParentKey(key: String): Option[String] = {
    val splitedList = key.split("/")
    if(splitedList.length > 1){
      val parentKey = splitedList.dropRight(1).mkString("/")
      return Some(parentKey)
    } else {
      return None
    }
  }
  
  /**
    * takes a block of cide and logs the time it takes to execute that block
    *
    * @param block
    * @param msg
    * @tparam R
    * @return
    */
  def time[R](block: => R, msg: String =""): R = {

    val t0 = System.currentTimeMillis()
    val result = block    // call-by-name
    val t1 = System.currentTimeMillis()
    logger.info(msg + (t1 - t0) + " ms")
    result
  }
}