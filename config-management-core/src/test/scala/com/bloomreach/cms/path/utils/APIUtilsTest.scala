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

import org.json4s.Diff
import org.json4s.JsonAST.{JNothing, JArray}
import org.json4s.jackson.JsonMethods
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite


/**
 * @author amit.kumar
 *
 */
class APIUtilsTest extends FunSuite with BeforeAndAfter {
  
  test("update json method") {
    val data = getJsonStringFromFile("./src/test/resources/config.json")
    val pathToNewValues = Map[String, Any]("config/level1/level2[1]/settings/down" -> 1, 
                                           "config/level1/level2[2]/name" -> "My Data",
                                           """config/level1/level2[id="1"]/status/validate_configuration""" -> "incomplete",
                                           """config/level1/level2[id="1"]/mapping[1]/is_attribute""" -> true)
    val updatedJson = APIUtils.updateJson(data, pathToNewValues)
    
    val expectedData = getJsonStringFromFile("./src/test/resources/config1.json")
    val expectedResult = JsonMethods.parse(expectedData)
    val actualResult = JsonMethods.parse(updatedJson)
    
    val Diff(changed, added, deleted) =  expectedResult diff actualResult
    assertResult(JNothing)(changed)
    assertResult(JNothing)(added)
    assertResult(JNothing)(deleted)
  }

  test("test updated json method with data") {
    val data = getJsonStringFromFile("./src/test/resources/level2.json")
    val valueToWrite = APIUtils.getValueForUpdate("""{"name": {"My Data" : "wrong data name"}}""", "name")
    val pathToNewValues = Map[String, Any]("[1]/name" -> valueToWrite)
    val updatedJson = APIUtils.updateJson(data, pathToNewValues)
    val updatedJsonData = JsonMethods.parse(updatedJson).asInstanceOf[JArray]
    val nameJson = updatedJsonData.arr(0) \ "name"
    assertResult(JsonMethods.parse("""{"My Data" : "wrong data name"}"""))(nameJson)
  }
  
  test("test update json method with data search case") {
    val initialData = getJsonStringFromFile("./src/test/resources/level2.json")
    val dataToWrite = APIUtils.getValueForUpdate(getJsonStringFromFile("./src/test/resources/level2_1.json"), """level2[id="1"]""")
    val pathToNewValues = Map[String, Any]("""[id="1"]""" -> dataToWrite)
    val updatedJson = APIUtils.updateJson(initialData, pathToNewValues)
    val expectedData = getJsonStringFromFile("./src/test/resources/level2_expected.json")
    
    val Diff(changed, added, deleted) =  JsonMethods.parse(expectedData) diff JsonMethods.parse(updatedJson)
    assertResult(JNothing)(changed)
    assertResult(JNothing)(added)
    assertResult(JNothing)(deleted)
  }
  
  test("test update json method with data array case") {
    val initialData = getJsonStringFromFile("./src/test/resources/level2.json")
    val dataToWrite = APIUtils.getValueForUpdate(getJsonStringFromFile("./src/test/resources/level2_1.json"), """level2[1]""")
    val pathToNewValues = Map[String, Any]("""[1]""" -> dataToWrite)
    val updatedJson = APIUtils.updateJson(initialData, pathToNewValues)
    val expectedData = getJsonStringFromFile("./src/test/resources/level2_expected.json")
    
    val Diff(changed, added, deleted) =  JsonMethods.parse(expectedData) diff JsonMethods.parse(updatedJson)
    assertResult(JNothing)(changed)
    assertResult(JNothing)(added)
    assertResult(JNothing)(deleted)
  }
  
  test("test getValueForUpdate method") {
    val result1 = APIUtils.getValueForUpdate("""{"name": "My Data"}""", "name")
    val result2 = APIUtils.getValueForUpdate("""{"name": 1}""", "name")
    assertResult("My Data")(result1)
    assertResult(1)(result2)
  }
  
  test("create json path") {
    val jsonPath1 = APIUtils.createJsonPath("""config/level1/level2[id="1"]/mapping[1]/is_attribute""")
    assertResult("""$['config']['level1']['level2'][?(@.id=="1")]['mapping'][0]['is_attribute']""")(jsonPath1)
    
    val jsonPath2 = APIUtils.createJsonPath("""config/level1/level2[1]/settings/down""")
    assertResult("""$['config']['level1']['level2'][0]['settings']['down']""")(jsonPath2)
    
    val jsonPath3 = APIUtils.createJsonPath("""config/level1/level2[id="1"]/status/validate_configuration""")
    assertResult("""$['config']['level1']['level2'][?(@.id=="1")]['status']['validate_configuration']""")(jsonPath3)
    
    val jsonPath4 = APIUtils.createJsonPath("name")
    assertResult("""$['name']""")(jsonPath4)
  }

  test("testing Common Prefix Path") {

    val prefix1 = APIUtils.commonPrefixPath(List("TEST/example1/level1", "TEST/example1/level2"))
    assertResult("TEST/example1")(prefix1)

    val prefix2 = APIUtils.commonPrefixPath(List("TEST/example2/level1", "TEST/example1/level2"))
    assertResult("TEST")(prefix2)

    val prefix3 = APIUtils.commonPrefixPath(List("TEST/example3/level1", "PRODUCTION/example2/level1"))
    assertResult("")(prefix3)

  }
  
  def getJsonStringFromFile(filePath: String) : String = {
    val source = Source.fromFile(filePath)
    val jsonString = try source.mkString finally source.close()
    return jsonString
  }
}