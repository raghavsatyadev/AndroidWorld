package io.github.raghavsatyadev.androidworld.core.application.layout

import io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppUseCase
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState
import io.github.raghavsatyadev.androidworld.core.domain.device.selectedSerialOrNull
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.foreground.ForegroundState
import io.github.raghavsatyadev.androidworld.core.domain.layout.AccessibilityAudit
import io.github.raghavsatyadev.androidworld.core.domain.layout.AccessibilityFinding
import io.github.raghavsatyadev.androidworld.core.domain.layout.AccessibilityReport
import io.github.raghavsatyadev.androidworld.core.domain.layout.InspectorTree
import io.github.raghavsatyadev.androidworld.core.domain.layout.NodeProperties
import io.github.raghavsatyadev.androidworld.core.domain.capture.PngSize
import io.github.raghavsatyadev.androidworld.core.domain.layout.TreeRow
import io.github.raghavsatyadev.androidworld.core.domain.layout.UiNode
import io.github.raghavsatyadev.androidworld.core.domain.layout.findings
import io.github.raghavsatyadev.androidworld.core.domain.layout.forNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One frozen capture of the inspector (design §9): screenshot, hierarchy, where and when. */
data class InspectorCapture(
    val snapshot: LayoutSnapshot,
    val serial: DeviceSerial,
    val deviceName: String,
    val activity: String?,
    val capturedAtMillis: Long,
) {
    val audit: AccessibilityReport by lazy { AccessibilityAudit.audit(snapshot.hierarchy) }
    val findings: List<AccessibilityFinding> by lazy { audit.findings() }
    val imageSize: Pair<Int, Int>? by lazy { PngSize.of(snapshot.png) }
    val imageScale: Double by lazy {
        val width = imageSize?.first ?: 0
        val rootWidth = snapshot.hierarchy.root.bounds.right
        if (width > 0 && rootWidth > 0) width.toDouble() / rootWidth.toDouble() else 1.0
    }

    fun imageToDevice(x: Double, y: Double): Pair<Int, Int> =
        (x / imageScale).toInt() to (y / imageScale).toInt()
}

/** The inspector tab's state (design §9 state pills). */
data class LayoutInspectorState(
    val capture: InspectorCapture? = null,
    val capturing: Boolean = false,
    val error: String? = null,
    /** The capture's device is no longer online: the capture stays readable, Re-capture is disabled. */
    val deviceGone: Boolean = false,
    val deviceOnline: Boolean = false,
    val deviceName: String? = null,
    val selectedNodeId: Int? = null,
    val expanded: Set<Int> = emptySet(),
    val highlightIssues: Boolean = true,
) {
    val rows: List<TreeRow>
        get() = capture?.let { InspectorTree.rows(it.snapshot.hierarchy.root, expanded) } ?: emptyList()

    val selectedNode: UiNode?
        get() = selectedNodeId?.let { capture?.snapshot?.hierarchy?.node(it) }

    val properties: List<Pair<String, String>>
        get() {
            val node = selectedNode ?: return emptyList()
            val hierarchy = capture?.snapshot?.hierarchy ?: return emptyList()
            return NodeProperties.of(node, hierarchy)
        }

    val selectedFindings: List<AccessibilityFinding>
        get() = selectedNodeId?.let { id -> capture?.findings?.forNode(id) } ?: emptyList()
}

/**
 * One Layout Inspector editor tab (task 064, design §9): captures the selected device's screen and
 * hierarchy, keeps the capture when the device goes away, and re-captures in place.
 */
class LayoutInspectorViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val capture: CaptureLayoutUseCase,
    private val currentApp: CurrentAppUseCase,
    private val selected: StateFlow<SelectedDeviceState>,
    private val deviceName: (DeviceSerial) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(LayoutInspectorState())
    val state: StateFlow<LayoutInspectorState> = _state.asStateFlow()

    init {
        scope.launch(dispatchers.default) {
            selected.collect { device ->
                val online = device is SelectedDeviceState.Online
                val serial = device.selectedSerialOrNull
                _state.update { state ->
                    state.copy(
                        deviceOnline = online,
                        deviceName = serial?.let(deviceName),
                        deviceGone = state.capture != null && !(online && serial == state.capture.serial),
                    )
                }
            }
        }
    }

    fun onShown() {
        val s = _state.value
        if (s.capture == null && !s.capturing && selected.value is SelectedDeviceState.Online) {
            recapture()
        }
    }

    fun select(id: Int?) {
        _state.update { state ->
            if (id == null) {
                state.copy(selectedNodeId = null)
            } else {
                val root = state.capture?.snapshot?.hierarchy?.root
                val node = state.capture?.snapshot?.hierarchy?.node(id)
                if (root != null && node != null) {
                    val ancestors = InspectorTree.pathTo(root, id).dropLast(1).map { it.id }.toSet()
                    state.copy(selectedNodeId = id, expanded = state.expanded + ancestors)
                } else {
                    state.copy(selectedNodeId = id)
                }
            }
        }
    }

    fun selectAt(imageX: Double, imageY: Double) {
        val cap = _state.value.capture ?: return
        val (deviceX, deviceY) = cap.imageToDevice(imageX, imageY)
        val hit = cap.snapshot.hierarchy.hitTest(deviceX, deviceY)
        select(hit?.id)
    }

    fun toggle(id: Int) {
        _state.update { state ->
            val nextExpanded = if (id in state.expanded) state.expanded - id else state.expanded + id
            state.copy(expanded = nextExpanded)
        }
    }

    fun expandAll() {
        _state.update { state ->
            val root = state.capture?.snapshot?.hierarchy?.root ?: return@update state
            state.copy(expanded = InspectorTree.allParents(root))
        }
    }

    fun collapseAll() {
        _state.update { state ->
            val root = state.capture?.snapshot?.hierarchy?.root ?: return@update state
            state.copy(expanded = setOf(root.id))
        }
    }

    fun setHighlightIssues(on: Boolean) {
        _state.update { it.copy(highlightIssues = on) }
    }

    fun recapture() {
        val device = selected.value as? SelectedDeviceState.Online ?: return
        val serial = device.device.serial
        if (_state.value.capturing) return
        _state.update { it.copy(capturing = true, error = null) }
        scope.launch(dispatchers.io) {
            val result = capture.snapshot(serial)
            val activity = runCatching {
                when (val front = currentApp.read(serial).foreground) {
                    is ForegroundState.App -> "${front.packageName}/${front.activity}"
                    is ForegroundState.SystemUi -> "System UI"
                    is ForegroundState.Home -> "${front.launcherPackage} (home)"
                    else -> null
                }
            }.getOrNull()
            _state.update { state ->
                when (result) {
                    is LayoutCapture.Captured -> {
                        val newCapture = InspectorCapture(result.value, serial, deviceName(serial), activity, now())
                        state.copy(
                            capture = newCapture,
                            capturing = false,
                            error = null,
                            deviceGone = false,
                            expanded = InspectorTree.allParents(newCapture.snapshot.hierarchy.root),
                            selectedNodeId = null,
                        )
                    }
                    is LayoutCapture.Failed -> state.copy(capturing = false, error = result.reason)
                }
            }
        }
    }
}
