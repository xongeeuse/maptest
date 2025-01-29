package com.example.osmprac.map

import android.content.Context
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RouteManager(private val context: Context) {
    // OSRMRoadManager 인스턴스 생성 및 초기화
    private val roadManager = OSRMRoadManager(context, "OsmPrac/1.0").apply {
        // 보행자 모드로 설정
        setMean(OSRMRoadManager.MEAN_BY_FOOT)
        // 경로 계산을 위한 추가 옵션 설정
        addRequestOption("continue_straight=true")  // 경유지에서 직진 우선
        addRequestOption("alternatives=false")      // 대체 경로 비활성화
    }

    /**
     * 주어진 경유지점들을 사용하여 경로를 계산하는 함수
     * @param waypoints 경유할 지점들의 리스트
     * @return 계산된 경로 (Road) 또는 실패 시 null
     */
    suspend fun calculateRoute(waypoints: ArrayList<GeoPoint>): Road? {
        return withContext(Dispatchers.IO) {
            try {
                // 경유지점들을 적절한 간격으로 처리
                val processedWaypoints = processWaypoints(waypoints)
                // 백그라운드 스레드에서 경로 계산
                roadManager.getRoad(processedWaypoints)
            } catch (e: Exception) {
                // 오류 발생 시 로그 출력 및 null 반환
                println("Error calculating route: ${e.message}")
                null
            }
        }
    }

    /**
     * 경유지점들을 적절한 간격으로 처리하는 함수
     * @param waypoints 원본 경유지점 리스트
     * @return 처리된 경유지점 리스트
     */
    private fun processWaypoints(waypoints: ArrayList<GeoPoint>): ArrayList<GeoPoint> {
        val processed = ArrayList<GeoPoint>()
        if (waypoints.size <= 2) return waypoints

        // 시작점 추가
        processed.add(waypoints.first())

        // 중간 지점들을 모두 추가 (간격 조정 없이)
        for (i in 1 until waypoints.size - 1) {
            processed.add(waypoints[i])
        }

        // 도착점 추가
        processed.add(waypoints.last())
        return processed
    }

    /**
     * 출발지와 도착지만으로 경로를 계산하는 함수
     * @param start 출발 지점
     * @param end 도착 지점
     * @return 계산된 경로 (Road) 또는 실패 시 null
     */
    suspend fun calculateRouteFromStartToEnd(start: GeoPoint, end: GeoPoint): Road? {
        val waypoints = ArrayList<GeoPoint>().apply {
            add(start)
            add(end)
        }
        return calculateRoute(waypoints)
    }

    /**
     * 보행자 모드로 설정하는 함수
     */
    fun setWalkingMode() {
        roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT)
    }

    /**
     * 경로의 총 거리를 km 단위로 포맷팅하는 함수
     * @param road 계산된 경로 객체
     * @return 거리를 km 단위로 포맷팅한 문자열
     */
    fun getFormattedDistance(road: Road): String {
        val distanceInMeters = road.mLength
        val distanceInKm = distanceInMeters / 1000.0
        return String.format("%.2f km", distanceInKm)
    }
}
