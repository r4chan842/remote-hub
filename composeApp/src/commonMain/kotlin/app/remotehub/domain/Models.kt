package app.remotehub.domain

enum class ServerState {
    Provisioning,
    Ready,
    Connected,
    Expiring,
    Stopping,
    Stopped,
    Failed,
}

enum class StepStatus {
    Pending,
    Running,
    Done,
    Failed,
    Skipped,
}

enum class AuthMethod {
    DeviceFlow,
    PersonalAccessToken,
}

enum class DeviceFlowPhase {
    Idle,
    CodeIssued,
    WaitingForApproval,
    Approved,
    Denied,
    Expired,
}

enum class OperationKind {
    Provision,
    Stop,
    Restart,
    Rebuild,
    Backup,
    Restore,
    RerunWorkflow,
    Connect,
}

enum class OperationResult {
    Success,
    Failure,
    Cancelled,
    InProgress,
}

enum class NetworkState {
    Offline,
    Authenticating,
    Connecting,
    Online,
    Error,
}

data class GitHubAccount(
    val login: String,
    val displayName: String,
    val method: AuthMethod,
    val grantedScopes: List<String>,
    val missingScopes: List<String>,
    val tokenExpiresLabel: String,
)

data class DeviceFlowState(
    val phase: DeviceFlowPhase,
    val userCode: String,
    val verificationUrl: String,
    val secondsRemaining: Int,
    val pollIntervalSeconds: Int,
)

data class TailnetStatus(
    val state: NetworkState,
    val tailnetName: String,
    val nodeName: String,
    val nodeAddress: String,
    val relayLabel: String,
    val latencyMillis: Int,
    val transportLabel: String,
    val lastError: String?,
)

data class ProvisionStep(
    val id: String,
    val status: StepStatus,
    val detail: String?,
    val elapsedMillis: Long?,
)

data class ConnectionInfo(
    val address: String,
    val port: Int,
    val username: String,
    val password: String,
    val certificateFingerprint: String,
)

data class ServerSpec(
    val cpuCores: Int,
    val memoryGigabytes: Int,
    val diskGigabytes: Int,
    val operatingSystem: String,
    val regionLabel: String,
    val providerLabel: String,
)

data class OperationRecord(
    val id: String,
    val kind: OperationKind,
    val result: OperationResult,
    val timestampLabel: String,
    val message: String,
)

data class BackupRecord(
    val id: String,
    val createdAtLabel: String,
    val sizeLabel: String,
    val encrypted: Boolean,
    val commitRef: String,
)

data class ServerSession(
    val id: String,
    val name: String,
    val state: ServerState,
    val spec: ServerSpec,
    val connection: ConnectionInfo?,
    val steps: List<ProvisionStep>,
    val uptimeLabel: String,
    val remainingLabel: String,
    val remainingFraction: Float,
    val warningThresholdMinutes: Int,
    val workflowRunNumber: Int,
    val workflowRunUrl: String,
    val repositoryFullName: String,
    val operations: List<OperationRecord>,
    val backups: List<BackupRecord>,
    val lastError: String?,
    val runId: Long = 0,
    val startedAtEpochMs: Long = 0,
    val durationMinutes: Int = 120,
)

data class QuotaSnapshot(
    val usedMinutes: Int,
    val includedMinutes: Int,
    val concurrentJobs: Int,
    val concurrentJobLimit: Int,
    val jobMaxDurationLabel: String,
)
