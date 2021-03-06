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

package batect.model.steps

import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerDoesNotExistException
import batect.docker.ContainerHealthCheckException
import batect.docker.ContainerRemovalFailedException
import batect.docker.ContainerStartFailedException
import batect.docker.ContainerStopFailedException
import batect.docker.DockerClient
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerImageBuildProgress
import batect.docker.HealthStatus
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.NetworkCreationFailedException
import batect.docker.NetworkDeletionFailedException
import batect.logging.Logger
import batect.model.RunAsCurrentUserConfigurationProvider
import batect.model.RunOptions
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerCreationFailedEvent
import batect.model.events.ContainerDidNotBecomeHealthyEvent
import batect.model.events.ContainerRemovalFailedEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.events.ContainerStartFailedEvent
import batect.model.events.ContainerStartedEvent
import batect.model.events.ContainerStopFailedEvent
import batect.model.events.ContainerStoppedEvent
import batect.model.events.ImageBuildFailedEvent
import batect.model.events.ImageBuildProgressEvent
import batect.model.events.ImageBuiltEvent
import batect.model.events.ImagePullFailedEvent
import batect.model.events.ImagePulledEvent
import batect.model.events.RunningContainerExitedEvent
import batect.model.events.TaskEventSink
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.events.TaskNetworkCreationFailedEvent
import batect.model.events.TaskNetworkDeletedEvent
import batect.model.events.TaskNetworkDeletionFailedEvent
import batect.model.events.TaskStartedEvent
import batect.model.events.TemporaryFileDeletedEvent
import batect.model.events.TemporaryFileDeletionFailedEvent
import batect.os.ProxyEnvironmentVariablesProvider
import java.io.IOException
import java.nio.file.Files

class TaskStepRunner(
    private val dockerClient: DockerClient,
    private val proxyEnvironmentVariablesProvider: ProxyEnvironmentVariablesProvider,
    private val creationRequestFactory: DockerContainerCreationRequestFactory,
    private val runAsCurrentUserConfigurationProvider: RunAsCurrentUserConfigurationProvider,
    private val logger: Logger
) {
    fun run(step: TaskStep, eventSink: TaskEventSink, runOptions: RunOptions) {
        logger.info {
            message("Running step.")
            data("step", step.toString())
        }

        when (step) {
            is BeginTaskStep -> eventSink.postEvent(TaskStartedEvent)
            is BuildImageStep -> handleBuildImageStep(step, eventSink, runOptions)
            is PullImageStep -> handlePullImageStep(step, eventSink)
            is CreateTaskNetworkStep -> handleCreateTaskNetworkStep(eventSink)
            is CreateContainerStep -> handleCreateContainerStep(step, eventSink, runOptions)
            is RunContainerStep -> handleRunContainerStep(step, eventSink)
            is StartContainerStep -> handleStartContainerStep(step, eventSink)
            is WaitForContainerToBecomeHealthyStep -> handleWaitForContainerToBecomeHealthyStep(step, eventSink)
            is StopContainerStep -> handleStopContainerStep(step, eventSink)
            is CleanUpContainerStep -> handleCleanUpContainerStep(step, eventSink)
            is RemoveContainerStep -> handleRemoveContainerStep(step, eventSink)
            is DeleteTemporaryFileStep -> handleDeleteTemporaryFileStep(step, eventSink)
            is DeleteTaskNetworkStep -> handleDeleteTaskNetworkStep(step, eventSink)
            is DisplayTaskFailureStep -> ignore()
            is FinishTaskStep -> ignore()
        }

        logger.info {
            message("Step completed.")
            data("step", step.toString())
        }
    }

    private fun handleBuildImageStep(step: BuildImageStep, eventSink: TaskEventSink, runOptions: RunOptions) {
        try {
            val onStatusUpdate = { p: DockerImageBuildProgress ->
                eventSink.postEvent(ImageBuildProgressEvent(step.container, p))
            }

            val image = dockerClient.build(step.projectName, step.container, proxyEnvironmentVariablesForOptions(runOptions), onStatusUpdate)
            eventSink.postEvent(ImageBuiltEvent(step.container, image))
        } catch (e: ImageBuildFailedException) {
            eventSink.postEvent(ImageBuildFailedEvent(step.container, e.message ?: ""))
        }
    }

    private fun handlePullImageStep(step: PullImageStep, eventSink: TaskEventSink) {
        try {
            val image = dockerClient.pullImage(step.imageName)
            eventSink.postEvent(ImagePulledEvent(image))
        } catch (e: ImagePullFailedException) {
            eventSink.postEvent(ImagePullFailedEvent(step.imageName, e.message ?: ""))
        }
    }

    private fun handleCreateTaskNetworkStep(eventSink: TaskEventSink) {
        try {
            val network = dockerClient.createNewBridgeNetwork()
            eventSink.postEvent(TaskNetworkCreatedEvent(network))
        } catch (e: NetworkCreationFailedException) {
            eventSink.postEvent(TaskNetworkCreationFailedEvent(e.outputFromDocker))
        }
    }

    private fun handleCreateContainerStep(step: CreateContainerStep, eventSink: TaskEventSink, runOptions: RunOptions) {
        try {
            val runAsCurrentUserConfiguration = runAsCurrentUserConfigurationProvider.generateConfiguration(step.container, eventSink)

            val creationRequest = creationRequestFactory.create(
                step.container,
                step.image,
                step.network,
                step.command,
                step.additionalEnvironmentVariables,
                runAsCurrentUserConfiguration.volumeMounts,
                runOptions.propagateProxyEnvironmentVariables,
                runAsCurrentUserConfiguration.userAndGroup
            )

            val dockerContainer = dockerClient.create(creationRequest)
            eventSink.postEvent(ContainerCreatedEvent(step.container, dockerContainer))
        } catch (e: ContainerCreationFailedException) {
            eventSink.postEvent(ContainerCreationFailedEvent(step.container, e.message ?: ""))
        }
    }

    private fun handleRunContainerStep(step: RunContainerStep, eventSink: TaskEventSink) {
        val result = dockerClient.run(step.dockerContainer)
        eventSink.postEvent(RunningContainerExitedEvent(step.container, result.exitCode))
    }

    private fun handleStartContainerStep(step: StartContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.start(step.dockerContainer)
            eventSink.postEvent(ContainerStartedEvent(step.container))
        } catch (e: ContainerStartFailedException) {
            eventSink.postEvent(ContainerStartFailedEvent(step.container, e.outputFromDocker))
        }
    }

    private fun handleWaitForContainerToBecomeHealthyStep(step: WaitForContainerToBecomeHealthyStep, eventSink: TaskEventSink) {
        try {
            val result = dockerClient.waitForHealthStatus(step.dockerContainer)

            val event = when (result) {
                HealthStatus.NoHealthCheck -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameHealthy -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameUnhealthy -> ContainerDidNotBecomeHealthyEvent(step.container, containerBecameUnhealthyMessage(step.dockerContainer))
                HealthStatus.Exited -> ContainerDidNotBecomeHealthyEvent(step.container, "The container exited before becoming healthy.")
            }

            eventSink.postEvent(event)
        } catch (e: ContainerHealthCheckException) {
            eventSink.postEvent(ContainerDidNotBecomeHealthyEvent(step.container, "Waiting for the container's health status failed: ${e.message}"))
        }
    }

    private fun containerBecameUnhealthyMessage(container: DockerContainer): String {
        val lastHealthCheckResult = dockerClient.getLastHealthCheckResult(container)

        val message = when {
            lastHealthCheckResult.exitCode == 0 -> "The most recent health check exited with code 0, which usually indicates that the container became healthy just after the timeout period expired."
            lastHealthCheckResult.output.isEmpty() -> "The last health check exited with code ${lastHealthCheckResult.exitCode} but did not produce any output."
            else -> "The last health check exited with code ${lastHealthCheckResult.exitCode} and output: ${lastHealthCheckResult.output.trim()}"
        }

        return "The configured health check did not indicate that the container was healthy within the timeout period. " + message
    }

    private fun handleStopContainerStep(step: StopContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.stop(step.dockerContainer)
            eventSink.postEvent(ContainerStoppedEvent(step.container))
        } catch (e: ContainerStopFailedException) {
            eventSink.postEvent(ContainerStopFailedEvent(step.container, e.outputFromDocker))
        }
    }

    private fun handleCleanUpContainerStep(step: CleanUpContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.forciblyRemove(step.dockerContainer)
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.outputFromDocker))
        } catch (_: ContainerDoesNotExistException) {
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        }
    }

    private fun handleRemoveContainerStep(step: RemoveContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.remove(step.dockerContainer)
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.outputFromDocker))
        } catch (_: ContainerDoesNotExistException) {
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        }
    }

    private fun handleDeleteTemporaryFileStep(step: DeleteTemporaryFileStep, eventSink: TaskEventSink) {
        try {
            Files.delete(step.filePath)
            eventSink.postEvent(TemporaryFileDeletedEvent(step.filePath))
        } catch (e: IOException) {
            eventSink.postEvent(TemporaryFileDeletionFailedEvent(step.filePath, e.toString()))
        }
    }

    private fun handleDeleteTaskNetworkStep(step: DeleteTaskNetworkStep, eventSink: TaskEventSink) {
        try {
            dockerClient.deleteNetwork(step.network)
            eventSink.postEvent(TaskNetworkDeletedEvent)
        } catch (e: NetworkDeletionFailedException) {
            eventSink.postEvent(TaskNetworkDeletionFailedEvent(e.outputFromDocker))
        }
    }

    private fun ignore() {
        // Do nothing.
    }

    private fun proxyEnvironmentVariablesForOptions(runOptions: RunOptions): Map<String, String> = if (runOptions.propagateProxyEnvironmentVariables) {
        proxyEnvironmentVariablesProvider.proxyEnvironmentVariables
    } else {
        emptyMap()
    }
}
