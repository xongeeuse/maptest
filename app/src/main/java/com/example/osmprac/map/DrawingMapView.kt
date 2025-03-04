package com.example.osmprac.map

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.example.osmprac.R
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.util.Log
import android.graphics.drawable.GradientDrawable



/**
 * 사용자로부터 점을 찍어 경유지를 입력받고, 이를 기반으로 경로를 계산 및 표시하는 커스텀 MapView 클래스
 */
class DrawingMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MapView(context, attrs) {

    // 사용자가 찍은 점들을 저장하는 리스트 (경유지)
    private val waypoints = mutableListOf<GeoPoint>()

    // 계산된 경로와 경로 오버레이를 저장하는 변수들
    private var road: Road? = null
    private var roadOverlay: Polyline? = null

    // 경유지 추가 모드 상태를 관리하는 변수
    private var isWaypointMode = false

    init {
        // 기본 지도 설정: 멀티터치 및 줌 컨트롤 활성화
        setMultiTouchControls(true)
        setBuiltInZoomControls(false)
    }

    /**
     * 경유지 추가 모드를 전환하는 함수
     * @return 전환된 모드의 상태
     */
    fun toggleDrawingMode(): Boolean {
        isWaypointMode = !isWaypointMode
        return isWaypointMode
    }

    /**
     * 지도 터치 이벤트 처리
     * 경유지 추가 모드일 때만 터치 이벤트를 처리하여 경유지 추가
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (isWaypointMode && event?.action == MotionEvent.ACTION_UP) {
            val point = projection.fromPixels(event.x.toInt(), event.y.toInt())
            val geoPoint = GeoPoint(point.latitude, point.longitude)
            addWaypoint(geoPoint)
            return true
        }
        return super.onTouchEvent(event)
    }

    // 새로운 함수 추가
    private fun updatePolyline() {
        // 기존 polyline 제거
        roadOverlay?.let { overlays.remove(it) }

        // 새로운 polyline 생성
        if (waypoints.size > 1) {  // 점이 2개 이상일 때만 선을 그림
            val polyline = Polyline().apply {
                setPoints(waypoints)
                outlinePaint.color = Color.BLUE
                outlinePaint.strokeWidth = 5.0f
            }

            overlays.add(polyline)
            roadOverlay = polyline
        }
        invalidate()
    }

    /**
     * 사용자가 찍은 점을 경유지로 추가하고 마커 표시
     * @param point 추가할 경유지의 위치
     */
    private fun addWaypoint(point: GeoPoint) {
        waypoints.add(point)

        Log.d("DrawPath", "새로운 경유지 추가: 위도=${point.latitude}, 경도=${point.longitude}")
        Log.d("DrawPath", "총 경유지 개수: ${waypoints.size}")

        // 동그라미 마커 생성
        val marker = Marker(this).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) // 중앙 정렬

            // 동그라미 drawable 생성
            val circleDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(40, 40) // dp 단위로 크기 설정

                when (waypoints.size) {
                    1 -> { // 출발지
                        setColor(Color.GREEN)
                        title = "출발"
                    }
                    waypoints.size -> { // 도착지
                        setColor(Color.RED)
                        title = "도착"
                    }
                    else -> { // 경유지
                        setColor(Color.BLUE)
                        title = "경유지 ${waypoints.size - 1}"
                    }
                }
            }

            icon = circleDrawable
        }

        overlays.add(marker)
        updatePolyline() // 선 업데이트
    }



    /**
     * 저장된 모든 경유지 목록 반환
     * @return 경유지 목록
     */
    fun getWaypoints(): List<GeoPoint> = waypoints.toList()

    /**
     * 모든 경유지와 경로를 초기화
     */
    fun clearWaypoints() {
        waypoints.clear()
        overlays.clear()
        road = null
        roadOverlay = null
        invalidate()
    }

    /**
     * 계산된 경로를 지도에 표시
     * @param calculatedRoad 표시할 경로 객체
     */
    fun showRoute(calculatedRoad: Road) {
        road = calculatedRoad

        // 기존 경로 제거
        roadOverlay?.let { overlays.remove(it) }

        // 새 경로 표시
        roadOverlay = RoadManager.buildRoadOverlay(calculatedRoad, Color.RED, 10f)
        overlays.add(roadOverlay)
        invalidate()
    }

    /**
     * 현재 표시된 경로 객체 반환
     * @return 현재 경로 객체 또는 null
     */
    fun getCurrentRoad(): Road? = road
}
