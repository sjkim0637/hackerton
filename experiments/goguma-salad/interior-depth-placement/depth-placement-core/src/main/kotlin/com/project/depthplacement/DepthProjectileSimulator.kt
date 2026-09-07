package com.project.depthplacement

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.tan

data class ProjectileConfig(
    val radiusMeters: Float = 0.07f,
    val launchSpeedMetersPerSecond: Float = 2.6f,
    val upwardBoostMetersPerSecond: Float = 0.25f,
    val gravityMetersPerSecondSquared: Float = 9.81f,
    val restitution: Float = 0.55f,
    val geometrySearchRadiusMeters: Float = 0.22f,
    val planeNeighborhoodMeters: Float = 0.18f,
    val maxLifetimeSeconds: Float = 8f,
)

data class ProjectileState(
    val position: Vec3,
    val velocity: Vec3,
    val radiusMeters: Float,
    val active: Boolean,
    val bounceCount: Int,
    val lastCollisionPoint: Vec3?,
    val lastCollisionNormal: Vec3?,
    val launchYawDegrees: Float,
    val launchPitchDegrees: Float,
    val ageSeconds: Float,
)

/** Lightweight debug projectile used to probe the latest IR Depth geometry. */
class DepthProjectileSimulator(private val config: ProjectileConfig = ProjectileConfig()) {
    private var geometryTimestamp = Long.MIN_VALUE
    private var geometry = emptyList<PointSample>()
    private var state: ProjectileState? = null
    private var lastStepNanos = 0L

    fun updateGeometry(snapshot: PointCloudSnapshot?) {
        if (snapshot == null || snapshot.timestampNanos == geometryTimestamp) return
        if (geometryTimestamp != Long.MIN_VALUE && snapshot.timestampNanos - geometryTimestamp in 0 until 100_000_000L) return
        val packed = snapshot.copyPoints()
        geometry = List(snapshot.pointCount) { index ->
            val offset = index * 4
            PointSample(Vec3(packed[offset], packed[offset + 1], packed[offset + 2]), 0, 0, packed[offset + 3])
        }
        geometryTimestamp = snapshot.timestampNanos
    }

    fun launch(
        cameraPose: CameraPose,
        yawOffsetDegrees: Float = 0f,
        pitchOffsetDegrees: Float = 0f,
        power: Float = 1f,
        timestampNanos: Long = System.nanoTime(),
    ): ProjectileState {
        val baseForward = cameraPose.forward()
        val right = cameraPose.right()
        val up = cameraPose.up()
        val direction = (
            baseForward +
                right * tan(Math.toRadians(yawOffsetDegrees.toDouble())).toFloat() +
                up * tan(Math.toRadians(pitchOffsetDegrees.toDouble())).toFloat()
            ).normalized()
        val launchSpeed = config.launchSpeedMetersPerSecond * (0.4f + 0.6f * power.coerceIn(0f, 1f))
        val velocity = direction * launchSpeed + up * config.upwardBoostMetersPerSecond
        val launched = ProjectileState(
            position = cameraPose.position() + direction * 0.22f - up * 0.08f,
            velocity = velocity,
            radiusMeters = config.radiusMeters,
            active = true,
            bounceCount = 0,
            lastCollisionPoint = null,
            lastCollisionNormal = null,
            launchYawDegrees = Math.toDegrees(atan2(direction.x.toDouble(), -direction.z.toDouble())).toFloat(),
            launchPitchDegrees = Math.toDegrees(asin(direction.y.coerceIn(-1f, 1f).toDouble())).toFloat(),
            ageSeconds = 0f,
        )
        state = launched
        lastStepNanos = timestampNanos
        return launched
    }

    fun currentState(): ProjectileState? = state

    fun step(timestampNanos: Long = System.nanoTime()): ProjectileState? {
        var current = state ?: return null
        if (!current.active) return current
        var remaining = ((timestampNanos - lastStepNanos).coerceAtLeast(0L) / 1_000_000_000f).coerceAtMost(0.15f)
        lastStepNanos = timestampNanos
        while (remaining > 0f && current.active) {
            val dt = minOf(remaining, 1f / 60f)
            current = integrate(current, dt)
            remaining -= dt
        }
        state = current
        return current
    }

    fun clear() { state = null; lastStepNanos = 0L }

    private fun integrate(current: ProjectileState, dt: Float): ProjectileState {
        val gravity = Vec3(0f, -config.gravityMetersPerSecondSquared, 0f)
        var velocity = current.velocity + gravity * dt
        var nextPosition = current.position + velocity * dt
        var bounceCount = current.bounceCount
        var collisionPoint = current.lastCollisionPoint
        var collisionNormal = current.lastCollisionNormal

        collision(current.position, nextPosition, velocity)?.let { hit ->
            nextPosition = hit.correctedCenter
            val normalSpeed = velocity.dot(hit.normal)
            velocity = velocity - hit.normal * ((1f + config.restitution) * normalSpeed)
            bounceCount++
            collisionPoint = hit.point
            collisionNormal = hit.normal
            if (velocity.length() < 0.18f && hit.normal.y > 0.65f) velocity = Vec3(0f, 0f, 0f)
        }

        val age = current.ageSeconds + dt
        val active = age < config.maxLifetimeSeconds && velocity.length() >= 0.02f && nextPosition.y > -5f
        return current.copy(position = nextPosition, velocity = velocity, active = active, bounceCount = bounceCount, lastCollisionPoint = collisionPoint, lastCollisionNormal = collisionNormal, ageSeconds = age)
    }

    private data class Collision(val correctedCenter: Vec3, val normal: Vec3, val point: Vec3)

    private fun collision(previous: Vec3, next: Vec3, velocity: Vec3): Collision? {
        if (geometry.size < 3) return null
        var nearest: PointSample? = null
        var nearestSquared = Float.POSITIVE_INFINITY
        for (point in geometry) {
            val d = point.position - next
            val squared = d.dot(d)
            if (squared < nearestSquared) { nearestSquared = squared; nearest = point }
        }
        val closest = nearest ?: return null
        if (nearestSquared > config.geometrySearchRadiusMeters * config.geometrySearchRadiusMeters) return null
        val neighborhoodSquared = config.planeNeighborhoodMeters * config.planeNeighborhoodMeters
        val neighbors = geometry.filter {
            val d = it.position - closest.position
            d.dot(d) <= neighborhoodSquared
        }
        val fit = LocalSurfaceEstimator.fit(neighbors) ?: return null
        var normal = fit.normal
        var previousDistance = (previous - fit.center).dot(normal)
        if (previousDistance < 0f) { normal = normal * -1f; previousDistance = -previousDistance }
        val nextDistance = (next - fit.center).dot(normal)
        if (velocity.dot(normal) >= 0f || previousDistance < config.radiusMeters * 0.5f || nextDistance > config.radiusMeters) return null
        val center = next + normal * (config.radiusMeters - nextDistance)
        return Collision(center, normal, center - normal * config.radiusMeters)
    }
}
