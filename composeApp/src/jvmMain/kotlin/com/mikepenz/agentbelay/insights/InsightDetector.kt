package com.mikepenz.agentbelay.insights

/**
 * A detector inspects one [SessionMetrics] and returns the insights it
 * applies to. Detectors are stateless and pure; the engine fans them out
 * per session.
 *
 * Implementations live under `insights/detectors/` and are registered in
 * [InsightEngine.defaultDetectors].
 */
fun interface InsightDetector {
    fun detect(session: SessionMetrics): List<Insight>
}
