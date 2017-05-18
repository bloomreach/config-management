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
package com.bloomreach.cms.redis

import scala.io.Source
import org.json4s.JsonAST.{JValue, JString, JObject, JInt, JField, JArray, JNothing}
import org.json4s.jackson.JsonMethods
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.Tag
import org.json4s.Diff
import java.nio.file.Files
import java.io.File

object IntegrationTest extends Tag("com.bloomreach.cms.redis.IntegrationTest")

/**
 * @author amit.kumar
 *
 */
class CacheIntegrationTests extends FunSuite with BeforeAndAfter {
  
  var cacheUpdater: CacheUpdater = _
  var cacheReader: CacheReader = _
  var cache: Cache = _
  var templateJson: JValue = _
  before {
    cacheUpdater = new CacheUpdater("localhost", 6379)
    cacheReader = new CacheReader("localhost", 6379)
    cache = Cache("localhost", 6379)
    val templateFilePath = "./src/test/resources/uberTemplate.json"
    val templateSource = Source.fromFile(templateFilePath)
    val templateJsonString = try templateSource.mkString finally templateSource.close()
    templateJson = JsonMethods.parse(templateJsonString).asInstanceOf[JValue]
    cache.flushCache()
  }
  
  test("Test cache updating", IntegrationTest) {
    val config = new CMSConfig("TEST", "merchants", "example_merchant", "testKey", templateJson)
    cache.add(config)
    val value = cache.get("merchants/example_merchant/TEST/testKey").get
    assertResult(value)(templateJson)
    assertResult(true)(cache.isKeyPresentInCache("merchants/example_merchant/TEST/testKey"))
    
    cache.rename("merchants/example_merchant/TEST/testKey", "merchants/example_merchant/TEST/newTestKey")
    assertResult(false)(cache.isKeyPresentInCache("merchants/example_merchant/TEST/testKey"))
    assertResult(true)(cache.isKeyPresentInCache("merchants/example_merchant/TEST/newTestKey"))
    val newValue = cache.get("merchants/example_merchant/TEST/newTestKey").get
    assertResult(newValue)(templateJson)
    
    cache.flushCache()
  }
  
  test("Test update recursively with a mid level key", IntegrationTest) {
    //First insert root level key
    val configKey = "config"
    val configJsonString = getConfigJsonString("./src/test/resources/testDocument.json")
    cacheUpdater.updateRecursively("TEST", "merchants", "example_merchant", configKey, configJsonString)
    
    //Now get the config1
    val config1Key = "config/config1"
    val resultBeforeUpdate = cache.get(s"merchants/example_merchant/TEST/$config1Key") match {
      case Some(x) => x
      case _ => None
    }
    
    //Then insert one child with change
    val customerKey = s"$config1Key/customer"
    val customerJsonString = """{"customer": "test_customer"}"""
    cacheUpdater.updateRecursively("TEST", "merchants", "example_merchant", customerKey, customerJsonString)
    
    //Now assert values for config1 key
    val resultAfterUpdate = cache.get(s"merchants/example_merchant/TEST/$config1Key") match {
      case Some(x) => x
      case _ => None
    }
    val Diff(changed, added, deleted) =  resultBeforeUpdate.asInstanceOf[JValue] diff resultAfterUpdate.asInstanceOf[JValue]
    assertResult(JNothing)(deleted)
    assertResult(JNothing)(added)
    assertResult(JObject(List(JField("config1", 
                   JObject(List(JField("customer", JString("test_customer"))))
                ))))(changed)
    
    //Now assert values for customer key
    val resultCustomer = cache.get(s"merchants/example_merchant/TEST/$customerKey") match {
      case Some(x) => x
      case _ => None
    }
    val Diff(changedCustomer, addedCustomer, deletedCustomer) =  JsonMethods.parse(customerJsonString) diff resultCustomer.asInstanceOf[JValue]
    assertResult(JNothing)(changedCustomer)
    assertResult(JNothing)(addedCustomer)
    assertResult(JNothing)(deletedCustomer)
    
    cache.flushCache()
  }
  
  test("Test nested get", IntegrationTest) {
    val key = "config"
    val testDocumentFilePath = "./src/test/resources/testDocument.json"
    val testDocumentSource = Source.fromFile(testDocumentFilePath)
    val testDocumentJsonString = try testDocumentSource.mkString finally testDocumentSource.close()
    cacheUpdater.updateRecursively("TEST", "merchants", "example_merchant", key, testDocumentJsonString)
    
    val mltDefaultResponseFields = cacheUpdater.getNestedValue("merchants/example_merchant/TEST/config/config1/default-key").get
    assertResult(JObject(List(JField("default-key", JString("key1")))))(mltDefaultResponseFields)
    
    val config5 = cacheUpdater.getNestedValue("merchants/example_merchant/TEST/config/config4[1]/config5")
    assertResult(JObject(List(JField("config5", JString("This_Year")))))(config5.get)
    
    cache.flushCache()
  }
  
  def createTempFolder(path: String): String = {
    val tempDirPath = Files.createTempDirectory(path).toString
    return tempDirPath
  }

  def deleteTempFolder(f: File): Unit =  {
    if (f.isDirectory()) {
      f.listFiles().toList.foreach(deleteTempFolder(_))
    }
    f.delete()
  }
  
  def getConfigJsonString(filePath: String): String = {
    val source = Source.fromFile(filePath)
    val jsonString = try source.mkString finally source.close()
    return jsonString
  }
}