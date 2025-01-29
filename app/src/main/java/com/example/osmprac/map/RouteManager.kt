package com.example.osmprac.map

import android.content.Context
import android.util.Log
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 경로 계산 및 관리를 담당하는 클래스
 * 경로 계산, 거리 계산, 시간 추정 등의 기능을 제공
 * @param context 애플리케이션 컨텍스트
 */
class RouteManager(private val context: Context) {
    companion object {
        private const val TAG = "RouteManager"
        private const val MIN_WAYPOINTS = 2
        private const val EARTH_RADIUS = 6371000.0 // 지구 반경 (미터)
        private const val MIN_VALID_DISTANCE = 10.0 // 최소 유효 거리 (미터)
    }

    /**
     * OSRMRoadManager 인스턴스 생성 및 초기화
     * 보행자 모드 및 경로 계산 옵션 설정
     */
    private val roadManager = OSRMRoadManager(context, "OsmPrac/1.0").apply {
        setMean(OSRMRoadManager.MEAN_BY_FOOT)
        addRequestOption("continue_straight=true")
        addRequestOption("alternatives=false")
    }

    /**
     * 주어진 경유지점들을 사용하여 경로를 계산하는 함수
     * @param waypoints 경유할 지점들의 리스트
     * @return 계산된 경로 (Road) 또는 실패 시 null
     */
    suspend fun calculateRoute(waypoints: List<GeoPoint>): Road? {
        if (waypoints.size < MIN_WAYPOINTS) {
            Log.e(TAG, "최소 2개 이상의 경유지가 필요합니다.")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                roadManager.getRoad(ArrayList(waypoints))
            } catch (e: Exception) {
                Log.e(TAG, "경로 계산 중 오류 발생: ${e.message}")
                null
            }
        }
    }

    /**
     * 보행자 모드로 설정하는 함수
     */
    fun setWalkingMode() {
        roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT)
    }

    /**
     * 경로의 총 거리를 계산하고 포맷팅하는 함수
     * OSRM 거리값이 비정상적일 경우 직접 계산
     * @param road 계산된 경로 객체
     * @return 거리를 적절한 단위로 포맷팅한 문자열
     */
    fun getFormattedDistance(road: Road): String {
        val distance = if (road.mLength < MIN_VALID_DISTANCE) {
            calculateTotalDistance(road.mRouteHigh)
        } else {
            road.mLength
        }
        return formatDistance(distance)
    }

    /**
     * Double 타입의 거리값을 포맷팅하는 함수
     * @param distanceInMeters 미터 단위의 거리 (Double)
     * @return 포맷팅된 거리 문자열
     */
    fun formatDistance(distanceInMeters: Double): String {
        return when {
            distanceInMeters < 1000 -> String.format("%.0f m", distanceInMeters)
            else -> String.format("%.2f km", distanceInMeters / 1000.0)
        }
    }

    /**
     * 경로점들의 총 거리를 계산하는 함수
     * @param points 경로를 구성하는 점들의 리스트
     * @return 총 거리 (미터)
     */
    private fun calculateTotalDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += calculateHaversineDistance(points[i], points[i + 1])
        }
        return totalDistance
    }

    /**
     * Haversine 공식을 사용하여 두 지점 간의 실제 거리를 계산하는 함수
     * @param point1 첫 번째 지점
     * @param point2 두 번째 지점
     * @return 두 지점 간의 거리(미터)
     */
    private fun calculateHaversineDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat/2).pow(2) +
                cos(lat1) * cos(lat2) *
                sin(dLon/2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1-a))

        return EARTH_RADIUS * c
    }

    /**
     * 경로의 예상 소요 시간을 계산하는 함수
     * 평균 보행 속도를 기준으로 계산
     * @param road 계산된 경로 객체
     * @return 예상 소요 시간을 포맷팅한 문자열
     */
    fun getEstimatedTime(road: Road): String {
        val durationInSeconds = road.mDuration
        val hours = (durationInSeconds / 3600).toInt()
        val minutes = ((durationInSeconds % 3600) / 60).toInt()
        return when {
            hours > 0 -> String.format("%d시간 %d분", hours, minutes)
            else -> String.format("%d분", minutes)
        }
    }
}
