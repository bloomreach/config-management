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

/**
 * @author amit.kumar
 * MetaData object for Search Queries
 * e.g. projectionSeparator = ","
 *      querySeparator = "\\+"
 *      queryKeyValueSeparator = ":"
 *      realmKey = "collection"
 *      wildcard = "*"
 */
class SearchQueryMetaData (val projectionSeparator: String,
                           val querySeparator: String, 
                           val queryKeyValueSeparator: String, 
                           val realmKey: String, 
                           val wildcard: String) {
}