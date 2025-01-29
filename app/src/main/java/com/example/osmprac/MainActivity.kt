package com.example.osmprac

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import com.example.osmprac.databinding.ActivityMainBinding
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.osmprac.location.LocationTracker
import com.example.osmprac.map.MapHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import com.example.osmprac.map.DrawingMapView
import com.example.osmprac.map.RouteManager
import com.example.osmprac.map.NavigationManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.View
import org.osmdroid.bonuspack.routing.Road
import android.util.Log
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {
    // 필요한 클래스 인스턴스 선언
    private lateinit var map: DrawingMapView
    private lateinit var mapHelper: MapHelper
    private lateinit var locationTracker: LocationTracker
    private lateinit var binding: ActivityMainBinding
    private lateinit var routeManager: RouteManager
    private lateinit var navigationManager: NavigationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSM 설정 초기화
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        // ViewBinding 초기화
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 지도 초기화
        map = binding.mapView
        setupMap()
        setupRouting()

        // RouteManager 초기화
        routeManager = RouteManager(this)

        // NavigationManager 초기화
        navigationManager = NavigationManager(this, map, routeManager)

        // 지도 헬퍼 초기화
        mapHelper = MapHelper(map)

        // 위치 추적 초기화
        locationTracker = LocationTracker(this) { location ->
            mapHelper.addRoutePoint(location.latitude, location.longitude)
            // 내비게이션 매니저 업데이트
            navigationManager.updateNavigation(location)?.let { info ->
                updateNavigationUI(info)
                // 30미터 이내일 때만 음성 안내
                if (info.distance < 30) {
                    navigationManager.provideVoiceGuidance(info.instruction)
                }
            }
        }

        // 권한 체크 및 요청
        checkLocationPermission()

        // 총 거리 TextView 초기에 숨기기
        binding.txtTotalDistance.visibility = View.GONE

        setupButtons()
    }

    // 라우팅 설정
    private fun setupRouting() {
        routeManager = RouteManager(this)
        routeManager.setWalkingMode()
    }

    // 버튼 설정
    private fun setupButtons() {
        // 그리기 버튼
        binding.btnDraw.setOnClickListener {
            map.toggleDrawingMode()
            if (map.isDrawingMode) {
                locationTracker.stopTracking()
            } else {
                locationTracker.startTracking()
            }
        }

        // 경로점 확인을 위한 테스트 코드
        binding.btnDraw.setOnLongClickListener {
            val points = map.getDrawnPath()
            Log.d("DrawPath", "총 좌표 개수: ${points.size}")
            points.forEach { point ->
                Log.d("DrawPath", "Latitude: ${point.latitude}, Longitude: ${point.longitude}")
            }
            true
        }

        // 경로 계산 버튼
        binding.btnCalculateRoute.setOnClickListener {
            calculateRoute()
        }

        // 내비게이션 시작 버튼
        binding.btnStartNavigation.setOnClickListener {
            startNavigation()
        }
    }

    // 경로 계산
    private fun calculateRoute() {
        lifecycleScope.launch {
            val drawnPoints = map.getDrawnPath()
            if (drawnPoints.size < 2) {
                // 최소 2개 포인트 필요
                return@launch
            }

            val startPoint = drawnPoints.first()
            val endPoint = drawnPoints.last()

            // 출발점 마커 추가
            val startMarker = Marker(map).apply {
                position = startPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "출발"
            }
            map.overlays.add(startMarker)

            // 도착점 마커 추가
            val endMarker = Marker(map).apply {
                position = endPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "도착"
            }
            map.overlays.add(endMarker)


            // 여기에 새로운 waypoints 코드를 추가
            val waypoints = ArrayList<GeoPoint>(drawnPoints)

            val road = routeManager.calculateRoute(waypoints)
            road?.let {
                map.showRoute(it)
                // 총 거리 계산 및 표시
                val formattedDistance = routeManager.getFormattedDistance(it)
                binding.txtTotalDistance.text = "총 거리: $formattedDistance"
                binding.txtTotalDistance.visibility = View.VISIBLE
            }
        }
    }


    // 내비게이션 시작 - 필터링 전
    private fun startNavigation() {
        locationTracker.startTracking()
        binding.navigationPanel.visibility = View.VISIBLE
        // 경로 포인트 설정
        val points = map.getDrawnPath()
        if (points.isNotEmpty()) {
            lifecycleScope.launch {
                Log.d("Navigation", "Setting route with ${points.size} points")
                // 전체 경로를 내비게이션 매니저에 전달
                navigationManager.setRoute(points)
                Log.d("Navigation", "Route set completed")
            }
        }
    }


    // 내비게이션 UI 업데이트
    private fun updateNavigationUI(info: NavigationManager.NavigationInfo) {
        binding.apply {
            txtNextInstruction.text = info.instruction
            txtDistance.text = "${info.distance.toInt()}m"
            // remainingDistance도 표시해야 함
            txtRemaining.text = "남은 거리: ${(info.remainingDistance / 1000).toInt()}km"
        }
    }

    // 지도 설정
    private fun setupMap() {
        map.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(true)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(37.5665, 126.9780))
        }
    }

    // 위치 권한 확인
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            locationTracker.startTracking()
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationTracker.startTracking()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationTracker.stopTracking()
    }

    override fun onDestroy() {
        super.onDestroy()
        navigationManager.cleanup()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}
