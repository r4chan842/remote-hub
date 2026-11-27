package app.remotehub.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GhUser(val login: String, val name: String? = null, val id: Long = 0)

@Serializable
data class GhRepo(
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true,
    val description: String = "",
)

@Serializable
data class RepoPublicKey(
    @SerialName("key_id") val keyId: String,
    val key: String,
)

@Serializable
data class PutSecretRequest(
    @SerialName("encrypted_value") val encryptedValue: String,
    @SerialName("key_id") val keyId: String,
)

@Serializable
data class ContentEntry(val sha: String, val path: String)

@Serializable
data class PutContentRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null,
)

@Serializable
data class PutContentResponse(val content: ContentEntry? = null, val commit: CommitRef? = null)

@Serializable
data class CommitRef(val sha: String)

@Serializable
data class DispatchRequest(val ref: String, val inputs: Map<String, String> = emptyMap())

@Serializable
data class RunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList(),
)

@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("run_number") val runNumber: Int = 0,
    val status: String,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("run_started_at") val runStartedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    val event: String = "",
)

@Serializable
data class JobsResponse(val jobs: List<WorkflowJob> = emptyList())

@Serializable
data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val steps: List<WorkflowStep> = emptyList(),
)

@Serializable
data class WorkflowStep(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int = 0,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class WorkflowsResponse(val workflows: List<Workflow> = emptyList())

@Serializable
data class Workflow(val id: Long, val name: String, val path: String, val state: String)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
data class DeviceTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
data class StoredServer(
    val id: String,
    val name: String,
    val repository: String,
    val runId: Long,
    val runNumber: Int,
    val runUrl: String,
    val startedAtEpochMs: Long,
    val durationMinutes: Int,
    val warningMinutes: Int,
    val address: String,
    val port: Int,
    val username: String,
    val password: String,
    val state: String,
)
