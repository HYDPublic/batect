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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.config.Container
import batect.docker.DockerContainer
import batect.logging.Logger
import batect.testutils.InMemoryLogSink
import batect.testutils.imageSourceDoesNotMatter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerBecameHealthyEventSpec : Spek({
    describe("a 'container became healthy' event") {
        val containerA = Container("container-a", imageSourceDoesNotMatter())
        val event = ContainerBecameHealthyEvent(containerA)

        describe("being applied") {
            describe("when the container that became healthy (A) is depended on by another container (B)") {
                val otherDependencyOfB = Container("other-dependency", imageSourceDoesNotMatter())
                val containerB = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf(containerA.name, otherDependencyOfB.name))

                val context = mock<TaskEventContext>()
                val logger = Logger("test.source", InMemoryLogSink())

                beforeEachTest {
                    reset(context)

                    whenever(context.containersThatDependOn(containerA)).doReturn(setOf(containerB))
                    whenever(context.dependenciesOf(containerB)).doReturn(setOf(containerA, otherDependencyOfB))
                }

                describe("and B's Docker container has been created") {
                    val containerBDockerContainer = DockerContainer("container-b-container")

                    beforeEachTest {
                        whenever(context.getPastEventsOfType<ContainerCreatedEvent>()).doReturn(setOf(
                                ContainerCreatedEvent(containerB, containerBDockerContainer)
                        ))
                    }

                    describe("and all other dependencies of container B have become healthy") {
                        beforeEachTest {
                            whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(
                                    event,
                                    ContainerBecameHealthyEvent(otherDependencyOfB)
                            ))
                        }

                        on("and container B is the task container") {
                            whenever(context.isTaskContainer(containerB)).doReturn(true)

                            event.apply(context, logger)

                            it("queues a 'run container' step for container B") {
                                verify(context).queueStep(RunContainerStep(containerB, containerBDockerContainer))
                            }
                        }

                        on("and container B is a dependency container") {
                            whenever(context.isTaskContainer(containerB)).doReturn(false)

                            event.apply(context, logger)

                            it("queues a 'start container' step for container B") {
                                verify(context).queueStep(StartContainerStep(containerB, containerBDockerContainer))
                            }
                        }
                    }

                    on("and not all other dependencies of container B have become healthy") {
                        whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(event))

                        event.apply(context, logger)

                        it("does not queue any further work") {
                            verify(context, never()).queueStep(any())
                        }
                    }
                }

                describe("and container B's Docker container has not been created yet") {
                    beforeEachTest {
                        whenever(context.getPastEventsOfType<ContainerCreatedEvent>()).doReturn(emptySet())
                    }

                    on("and all dependencies of B have become healthy") {
                        whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(
                                event,
                                ContainerBecameHealthyEvent(otherDependencyOfB)
                        ))

                        event.apply(context, logger)

                        it("does not queue any further work") {
                            verify(context, never()).queueStep(any())
                        }
                    }

                    on("and all dependencies of B have not become healthy") {
                        whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(event))

                        event.apply(context, logger)

                        it("does not queue any further work") {
                            verify(context, never()).queueStep(any())
                        }
                    }
                }
            }

            on("when the task is aborting") {
                val logger = Logger("test.source", InMemoryLogSink())
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                }

                event.apply(context, logger)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ContainerBecameHealthyEvent(container: 'container-a')"))
            }
        }
    }
})
