
package com.example.osmprac.map

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.example.osmprac.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class DrawingMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MapView(context, attrs) {

    // 현재 그리고 있는 경로를 저장할 Polyline
    private var currentPath: Polyline? = null

    // 저장된 모든 경로점들
    private val pathPoints = mutableListOf<GeoPoint>()

    // 그리기 모드 상태
    var isDrawingMode = false
        private set

    // 출발지와 도착지 저장 변수
    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null

    // 계산된 경로와 경로 오버레이
    private var road: Road? = null
    private var roadOverlay: Polyline? = null

    init {
        // 기본 설정
        setMultiTouchControls(true)
        setBuiltInZoomControls(false)
    }

    // 그리기 모드 전환
    fun toggleDrawingMode() {
        isDrawingMode = !isDrawingMode
        if (isDrawingMode) {
            // 새로운 경로 시작
            currentPath = Polyline().apply {
                outlinePaint.color = Color.RED
                outlinePaint.strokeWidth = 5f
            }
            overlays.add(currentPath)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (isDrawingMode && event != null) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 터치 시작 - 새로운 경로 시작
                    startNewPath(event)
                }
                MotionEvent.ACTION_MOVE -> {
                    // 터치 이동 - 경로에 점 추가
                    addPointToPath(event)
                }
                MotionEvent.ACTION_UP -> {
                    // 터치 종료 - 경로 완성
                    finishPath()
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun startNewPath(event: MotionEvent) {
        // IGeoPoint를 GeoPoint로 변환
        val point = projection.fromPixels(event.x.toInt(), event.y.toInt())
        val geoPoint = GeoPoint(point.latitude, point.longitude)
        currentPath?.points?.clear()
        currentPath?.addPoint(geoPoint)
        pathPoints.add(geoPoint)
        invalidate()
    }

    private fun addPointToPath(event: MotionEvent) {
        // IGeoPoint를 GeoPoint로 변환
        val point = projection.fromPixels(event.x.toInt(), event.y.toInt())
        val geoPoint = GeoPoint(point.latitude, point.longitude)
        currentPath?.addPoint(geoPoint)
        pathPoints.add(geoPoint)
        invalidate()
    }

    private fun finishPath() {
        // 경로 완성 시 필요한 처리
        isDrawingMode = false
    }

    // 저장된 경로점들 반환
    fun getDrawnPath(): List<GeoPoint> = pathPoints.toList()

    // 출발지 설정
    fun setStartPoint(point: GeoPoint) {
        startPoint = point
        addMarker(point, Color.GREEN)
    }

    // 도착지 설정
    fun setEndPoint(point: GeoPoint) {
        endPoint = point
        addMarker(point, Color.RED)
    }

    // 마커 추가
    private fun addMarker(point: GeoPoint, color: Int) {
        val marker = Marker(this)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(context, R.drawable.ic_marker)?.apply {
            setTint(color)
        }
        overlays.add(marker)
        invalidate()
    }

    // 경로 계산
    fun calculateRoute() {
        val start = startPoint
        val end = endPoint
        if (start != null && end != null) {
            val roadManager = OSRMRoadManager(context, "OsmPrac/1.0")
            roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT)

            GlobalScope.launch(Dispatchers.IO) {
                val waypoints = ArrayList<GeoPoint>()
                waypoints.add(start)
                waypoints.add(end)
                val road = roadManager.getRoad(waypoints)

                withContext(Dispatchers.Main) {
                    showRoute(road)
                }
            }
        }
    }

    // 계산된 경로 표시
    fun showRoute(calculatedRoad: Road) {
        road = calculatedRoad
        // 기존 경로 제거
        roadOverlay?.let { overlays.remove(it) }

        // 새 경로 표시
        roadOverlay = RoadManager.buildRoadOverlay(calculatedRoad, Color.BLUE, 10f)
        overlays.add(roadOverlay)
        invalidate()
    }

    // 경로 초기화
    fun clearRoute() {
        roadOverlay?.let { overlays.remove(it) }
        road = null
        invalidate()
    }
}
