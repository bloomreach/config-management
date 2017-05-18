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

import com.bloomreach.cms.redis.{CacheUpdater, CacheReader}
import com.typesafe.scalalogging.LazyLogging
import org.json4s.JsonAST._
import com.bloomreach.cms.redis.Constants
/**
  * @author neel.choudhury
  */

class SearchHandler(val cacheUpdater: CacheUpdater, val cacheReader: CacheReader, dbName: String) extends LazyLogging {

  /**
    * Methods takes a set of queries
    * It then find out which documents satisfies those queries
    * For all those document desired projections are returned
    * For eg for Selection set Set( FieldMatchingQuery("config/key1/key2", "true"))
    * and Projection Set("account-id")
    * The methods returns an array of account id for all documents whose key2 field is true
    * After getting the result we use the order by for ordering of elements in JArray
    * @param searchPathString
    * @param selectQueries
    * @param projectionFieldSet
    * @param searchOrderByHandler
    * @return
    */

  def searchOnPath( searchPathString: String,
                    selectQueries: List[SelectionEntity],
                    projectionFieldSet: Set[String],
                    searchOrderByHandler: SearchOrderByHandler
                  ): JValue = {

    // Get set of documents on which we need to process the query (can be Lazy or Full)
    val setOfDocuments = DocumentSearchPath(searchPathString, cacheUpdater, cacheReader).getDocumentList
    return searchOnDocuments(setOfDocuments, selectQueries, projectionFieldSet, searchOrderByHandler)
   }


  def searchOnDocuments( setOfDocuments: Set[_ <: SearchDocument],
                        selectQueries: List[SelectionEntity],
                        projectionFieldSet: Set[String],
                        searchOrderByHandler: SearchOrderByHandler
                      ): JValue = {

    val uberTemplate = cacheReader.getTemplate(dbName, Constants.UBER_SCHEMA_NAME)

    logger.debug("universe of documents :" + setOfDocuments)
    // Get all those documents which satisfy the selection conditions
    val selectedDocuments = SearchSelectionHandler(cacheUpdater, cacheReader, uberTemplate, selectQueries).getSelectedDocuments(setOfDocuments)
    logger.debug("selected documents :" + selectedDocuments)

    // From all the selected documents get the
    val projectedJArray = SearchProjectionHandler(cacheUpdater, cacheReader, uberTemplate, projectionFieldSet ).getProjectionFromDocuments(selectedDocuments)

    JArray(searchOrderByHandler.orderDocuments(projectedJArray.arr))

  }

}

