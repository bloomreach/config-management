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

import org.json4s.jackson.JsonMethods
import org.json4s.JsonAST.{JValue, JField, JNothing, JObject, JString}
import scala.io.Source
import scala.collection._
import org.json4s.JsonAST.JArray

/**
 * @author amit.kumar
 * Scala object to provide utility methods over cms template
 */
object TemplateUtils {
  
  val Properties = "properties"
  val Default = "default"
  val Ref = "$ref"
  val Required = "required"
  val Type = "type"
  val CmsAppInfo = "cms-appinfo"
  val FullKeyPath = "fullKeyPath"
  val AdditionalProperties = "additionalProperties"
  val Description = "description"
  val Label = "label"
  val AnyOf = "anyOf"
  val Enum = "enum"
  val MinItems = "minItems"
  val Items = "items"
  val SchemaKeywords = Set[String](Required, Type, CmsAppInfo, FullKeyPath, AdditionalProperties, Description, Label, AnyOf, Enum, MinItems)
  
  /**
   * @param key
   * @param uberTemplate
   * @return fullPathKey from the template
   */
  def getFullPathFromTemplate(key: String, uberTemplate: JValue): String = {
    val keyHierarchy = key.split("/")
    
    var currentTemplate = uberTemplate
    for(currentKey <- keyHierarchy) {
      currentTemplate = currentTemplate \\ currentKey
    }

    val fullPath = currentTemplate \ FullKeyPath
    return fullPath.asInstanceOf[JString].values.split("/").filter(x => !x.isEmpty).mkString("/")
  }
  
  /**
   * From a given schema extract config with default values only
   * @param schema
   * @return
   */
  def getConfigWithDefaultValues(schema: JValue): JValue = {
    val schemaJson = schema.asInstanceOf[JObject]
    val defaultConfig = schemaJson.removeField { 
      x => SchemaKeywords.contains(x._1)
    }
    //remove Properties Keys
    val configWithoutProperties = removeLevelWithName(Properties, defaultConfig.asInstanceOf[JObject])
    //remove Default Keys
    val configWithDefaultFlattened = removeLevelWithName(Default, configWithoutProperties.asInstanceOf[JObject])
    //remove items keys
    val defaultConfigs = replaceItemsKeyWithArray(configWithDefaultFlattened.asInstanceOf[JObject])
    return defaultConfigs
  }
  
  /**
   * removes items keys and wraps the value in an array
   * e.g. {config: {items: {a: b}}} will become {config [{a: b}]}
   * @param config
   * @return
   */
  def replaceItemsKeyWithArray(config: JObject): JValue = {
    val updatedConfig = config.transformField {
      case JField(k, v) => {
        val itemEntry = v \ Items
        if(itemEntry != JNothing) {
          JField(k, JArray(List(itemEntry)))
        } else {
          JField(k, v)
        }
      }
    }
    return updatedConfig
  }
  
  /**
   * removes all the occurrence of a key label and the inner object is moved one level up
   * @param label
   * @param config
   * @return
   */
  def removeLevelWithName(label: String, config: JObject): JValue = {
    val child = config \ label
    val updatedChild =  child match {
      //if label was not a key at first level, then look into children keys of the object
      case JNothing => {
        val keys = config.values.keySet
        val x = keys.map { 
          key => {
            val childConfig = (config \ key)
            if(childConfig.isInstanceOf[JObject]) {
              JField(key, removeLevelWithName(label, childConfig.asInstanceOf[JObject])) 
            } else {
              JField(key, childConfig) 
            }
          }
        }
        JObject(x.toList)
      }
      //if lable was a key at first level, then call the child recursively
      case JObject(_) => removeLevelWithName(label, child.asInstanceOf[JObject])
      case _ => child
    }
    return updatedChild
  }
  
  /**
   * @param default
   * @param concrete
   * @return merge concrete values on default values
   * In case of array for each element in array in concrete value merge with first element of default value
   */
  def mergeDefaultValueWithConcreteValue(default: JValue, concrete: JValue): Option[JValue] = {
    if(concrete == JNothing) {
      Some(default)
    } else {
      default match {
        case d: JArray => {
          val defaultValue = d.arr(0)
          val concreteValuesArray = concrete.asInstanceOf[JArray].arr
          val mergedArray = concreteValuesArray.map { (x => mergeDefaultValueWithConcreteValue(defaultValue, x).get) }
          Some(JArray(mergedArray))
        }
        
        case d: JObject => {
          val keys = d.values.keySet
          val mergedObject = JObject(keys.map { (key => JField(key, mergeDefaultValueWithConcreteValue(d \ key, concrete \ key).get)) }.toList)
          Some(mergedObject)
        }
        case _ => Some(default merge concrete)
      }
    }
  }
}