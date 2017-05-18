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

import scala.io.Source
import org.json4s.JsonAST.{JString, JNothing}
import org.scalatest.FunSuite
import org.json4s.jackson.JsonMethods
import org.json4s.Diff
import org.json4s.JsonAST.JValue
import com.bloomreach.cms.search.SearchUtils

/**
 * @author amit.kumar
 * Unit tests for TemplateUtils
 */
class TemplateUtilsTest extends FunSuite {
  
  test("Test Full path extraction from UberTemplate") {
    val key = "config1"
    val uberTemplate = parseJsonFromFile("./src/test/resources/uberTemplate.json")
    val fullPathForConfig1 = TemplateUtils.getFullPathFromTemplate(key, uberTemplate)
    assertResult("config/config1")(fullPathForConfig1)
    
    val fullPathForID = TemplateUtils.getFullPathFromTemplate("config/config1/id", uberTemplate)
    assertResult("config/config1/id")(fullPathForID)
  }
  
  test("test getConfigWithDefaultValues with uberTemplate") {
    val templateFilePath = "./src/test/resources/uberTemplate.json"
    val templateSource = Source.fromFile(templateFilePath)
    val templateJsonString = try templateSource.mkString finally templateSource.close()
    val actualConfig = TemplateUtils.getConfigWithDefaultValues(JsonMethods.parse(templateJsonString))
    
    val defaultValuesFilePath = "./src/test/resources/defaultValues.json"
    val defaultValuesSource = Source.fromFile(defaultValuesFilePath)
    val defaultValuesJsonString = try defaultValuesSource.mkString finally defaultValuesSource.close()
    val expectedConfig = JsonMethods.parse(defaultValuesJsonString)
    
    val Diff(changed, added, deleted) =  expectedConfig diff actualConfig
    assertResult(true)(JNothing == changed)
    assertResult(true)(JNothing == added)
    assertResult(true)(JNothing == deleted)
  }
  
  test("mergeDefaultValueWithConcreteValue") {
    val mergedValue1 = TemplateUtils.mergeDefaultValueWithConcreteValue(JsonMethods.parse("""{"config": {"live": false}}"""), JNothing)
    assertResult(JsonMethods.parse("""{"config": {"live": false}}"""))(mergedValue1.get)
    
    val mergedValue2 = TemplateUtils.mergeDefaultValueWithConcreteValue(
                                                                       JsonMethods.parse("""{"config": {"live": false, "config1": [{"config2": "all"}]}}"""), 
                                                                       JsonMethods.parse("""{"config": {"live": true}}"""))
                                                                       
    assertResult(JsonMethods.parse("""{"config": {"live": true, "config1": [{"config2": "all"}]}}"""))(mergedValue2.get)
    
    val mergedValue3 = TemplateUtils.mergeDefaultValueWithConcreteValue(JsonMethods.parse("""{"config": {"live": false, "config1": [{"override": "all", "key": "my_key"}]}}"""), 
                                                                        JsonMethods.parse("""{"config": {"live": true, "config1": [{"override": "none"}]}}"""))
    
    assertResult(JsonMethods.parse("""{"config": {"live": true, "config1": [{"override": "none", "key": "my_key"}]}}"""))(mergedValue3.get)
  }
  
  def parseJsonFromFile(filePath: String) : JValue = {
    return JsonMethods.parse(getJsonStringFromFile(filePath))
  }
  
  def getJsonStringFromFile(filePath: String) : String = {
    val source = Source.fromFile(filePath)
    val jsonString = try source.mkString finally source.close()
    return jsonString
  }
}