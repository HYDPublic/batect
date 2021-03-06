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

package batect.model.events

import batect.config.Container
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.logging.Logger
import batect.model.steps.CreateContainerStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.DeleteTemporaryFileStep
import batect.os.Command
import batect.testutils.InMemoryLogSink
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Paths

object ContainerRemovedEventSpec : Spek({
    describe("a 'container removed' event") {
        val container = Container("container-1", imageSourceDoesNotMatter())
        val event = ContainerRemovedEvent(container)

        describe("being applied") {
            val otherContainer1 = Container("other-container-1", imageSourceDoesNotMatter())
            val otherContainer2 = Container("other-container-2", imageSourceDoesNotMatter())
            val containerThatFailedToCreate = Container("failed-to-create", imageSourceDoesNotMatter())
            val image = DockerImage("some-image")
            val network = DockerNetwork("the-network")
            val command = Command.parse("some-command")
            val logger = Logger("test.source", InMemoryLogSink())

            on("when all containers with a pending or processed creation step have been removed or reported that they failed to be created") {
                val context = mock<TaskEventContext> {
                    on { getPendingAndProcessedStepsOfType<CreateContainerStep>() } doReturn setOf(
                        CreateContainerStep(container, command, emptyMap(), image, network),
                        CreateContainerStep(otherContainer1, command, emptyMap(), image, network),
                        CreateContainerStep(otherContainer2, command, emptyMap(), image, network),
                        CreateContainerStep(containerThatFailedToCreate, command, emptyMap(), image, network)
                    )

                    on { getPastEventsOfType<ContainerRemovedEvent>() } doReturn setOf(
                        event,
                        ContainerRemovedEvent(otherContainer1),
                        ContainerRemovedEvent(otherContainer2)
                    )

                    on { getPastEventsOfType<ContainerCreationFailedEvent>() } doReturn setOf(
                        ContainerCreationFailedEvent(containerThatFailedToCreate, "Some message")
                    )

                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                }

                event.apply(context, logger)

                it("queues a 'delete task network' step") {
                    verify(context).queueStep(DeleteTaskNetworkStep(network))
                }
            }

            on("when some containers with a pending or processed creation step have not been removed or reported that they failed to be created") {
                val context = mock<TaskEventContext> {
                    on { getPendingAndProcessedStepsOfType<CreateContainerStep>() } doReturn setOf(
                        CreateContainerStep(container, command, emptyMap(), image, network),
                        CreateContainerStep(otherContainer1, command, emptyMap(), image, network),
                        CreateContainerStep(otherContainer2, command, emptyMap(), image, network),
                        CreateContainerStep(containerThatFailedToCreate, command, emptyMap(), image, network)
                    )

                    on { getPastEventsOfType<ContainerRemovedEvent>() } doReturn setOf(
                        event,
                        ContainerRemovedEvent(otherContainer1)
                    )

                    on { getPastEventsOfType<ContainerCreationFailedEvent>() } doReturn setOf(
                        ContainerCreationFailedEvent(containerThatFailedToCreate, "Some message")
                    )
                }

                event.apply(context, logger)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }

            on("when temporary files were created for the container") {
                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<TemporaryFileCreatedEvent>() } doReturn setOf(
                        TemporaryFileCreatedEvent(container, Paths.get("/container-path-1")),
                        TemporaryFileCreatedEvent(container, Paths.get("/container-path-2")),
                        TemporaryFileCreatedEvent(otherContainer1, Paths.get("/container-path-3")),
                        TemporaryFileCreatedEvent(otherContainer2, Paths.get("/container-path-4"))
                    )

                    on { getPendingAndProcessedStepsOfType<CreateContainerStep>() } doReturn setOf(
                        CreateContainerStep(container, command, emptyMap(), image, network)
                    )

                    on { getPastEventsOfType<ContainerRemovedEvent>() } doReturn emptySet()
                    on { getPastEventsOfType<ContainerCreationFailedEvent>() } doReturn emptySet()
                }

                event.apply(context, logger)

                it("queues 'delete temporary file' steps for those files") {
                    verify(context).queueStep(DeleteTemporaryFileStep(Paths.get("/container-path-1")))
                    verify(context).queueStep(DeleteTemporaryFileStep(Paths.get("/container-path-2")))
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ContainerRemovedEvent(container: 'container-1')"))
            }
        }
    }
})
