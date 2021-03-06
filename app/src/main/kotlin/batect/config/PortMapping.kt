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

package batect.config

import com.fasterxml.jackson.annotation.JsonCreator

data class PortMapping(val localPort: Int, val containerPort: Int) {
    init {
        if (localPort <= 0) {
            throw InvalidPortMappingException("Local port must be positive.")
        }

        if (containerPort <= 0) {
            throw InvalidPortMappingException("Container port must be positive.")
        }
    }

    override fun toString(): String {
        return "$localPort:$containerPort"
    }

    companion object {
        @JvmStatic @JsonCreator
        fun parse(value: String): PortMapping {
            if (value == "") {
                throw IllegalArgumentException("Port mapping definition cannot be empty.")
            }

            val separator = ':'
            val separatorIndex = value.indexOf(separator)

            if (separatorIndex == -1) {
                throw invalidMappingDefinitionException(value)
            }

            val localString = value.substring(0, separatorIndex)
            val containerString = value.substring(separatorIndex + 1)

            if (localString == "" || containerString == "") {
                throw invalidMappingDefinitionException(value)
            }

            try {
                val localPort = localString.toInt()
                val containerPort = containerString.toInt()

                return PortMapping(localPort, containerPort)
            } catch (e: NumberFormatException) {
                throw invalidMappingDefinitionException(value, e)
            } catch (e: InvalidPortMappingException) {
                throw invalidMappingDefinitionException(value, e)
            }
        }

        fun invalidMappingDefinitionException(value: String, cause: Throwable? = null): Throwable = InvalidPortMappingException("Port mapping definition '$value' is not valid. It must be in the form 'local_port:container_port' and each port must be a positive integer.", cause)
    }
}

class InvalidPortMappingException(message: String, cause: Throwable? = null) : Exception(message, cause)
