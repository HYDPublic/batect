/*
   Copyright 2017-2018 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.os

import java.util.TreeSet

class ProxyEnvironmentVariablesProvider(private val hostEnvironmentVariables: Map<String, String>) {
    constructor() : this(System.getenv())

    private val proxyEnvironmentVariableNames: Set<String> = setOf("http_proxy", "https_proxy", "ftp_proxy", "no_proxy")
        .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))

    val proxyEnvironmentVariables: Map<String, String> by lazy {
        hostEnvironmentVariables.filterKeys { name -> proxyEnvironmentVariableNames.contains(name) }
    }
}
