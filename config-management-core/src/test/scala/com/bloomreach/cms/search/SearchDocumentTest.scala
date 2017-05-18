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
package com.bloomreach.cms.search

import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.Tag
import org.json4s.jackson.JsonMethods
import scala.io.Source
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JNothing
import org.json4s.Diff
import com.bloomreach.cms.redis.CacheUpdater
import com.bloomreach.cms.redis.CMSConfig
import com.bloomreach.cms.redis.CMSConfig

object IntegrationTest extends Tag("com.bloomreach.cms.search.IntegrationTest")

/**
 * @author amit.kumar
 *
 */
class SearchDocumentTest extends SearchTestBase {
  test("Test Full Document") {
    val fullDocument = FullDocument(parseJsonFromFile("./src/test/resources/testDocument.json"))
    val actualJson = fullDocument.getValueForKey("config1").get
    val expectedJsonString = """{
                                  "config1": {
                                    "id": 0,
                                    "default-key": "key1",
                                    "customer": "test_merchant"
                                  }
                                }"""
    
    val expectedJson = JsonMethods.parse(expectedJsonString)
    
    val Diff(changed, added, deleted) = expectedJson diff actualJson
    assertResult(true)(changed == JNothing && added == JNothing && deleted == JNothing)
    
    cacheUpdater.flushCache()
  }
  
  test("Test Lazy Document", IntegrationTest) {
    val cacheUpdater = new CacheUpdater("localhost", 6379)
    
    val expectedJsonString = """{
                                  "config1": {
                                    "id": 0,
                                    "default-key": "key1",
                                    "customer": "test_merchant"
                                  }
                                }"""
    
    val expectedJson = JsonMethods.parse(expectedJsonString)
    val fullConfig = parseJsonFromFile("./src/test/resources/testDocument.json")
    cacheUpdater.updateRecursively("TEST", "merchants", "sample_merchant", "config", JsonMethods.pretty(fullConfig))
    cacheUpdater.addTemplate("merchants", "uberTemplate", parseJsonFromFile("./src/test/resources/uberTemplate.json"))
    
    val cmsConfig = CMSConfig("TEST", "merchants", "sample_merchant", "config")
    val lazyDocument = LazyDocument(cmsConfig, cacheUpdater, cacheReader)
    val actualJson = lazyDocument.getValueForKey("config/config1").get
    
    val Diff(changed, added, deleted) = expectedJson diff actualJson
    assertResult(true)(changed == JNothing && added == JNothing && deleted == JNothing)
    
    cacheUpdater.flushCache()
  }
}