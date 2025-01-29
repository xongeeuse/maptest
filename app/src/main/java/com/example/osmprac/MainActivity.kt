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
import org.osmdroid.views.overlay.Marker
import android.util.Log

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
        // 경유지 추가 버튼
        binding.btnAddWaypoint.setOnClickListener {
            // 경유지 추가 모드 토글
            map.toggleDrawingMode()
        }

        // 경로 계산 버튼
        binding.btnCalculateRoute.setOnClickListener {
            calculateRoute()
        }

        // 내비게이션 시작 버튼
        binding.btnStartNavigation.setOnClickListener {
            startNavigation()
        }

        // 경로 초기화 버튼
        binding.btnClearRoute.setOnClickListener {
            clearRoute()
        }
    }

    // 경로 계산
    private fun calculateRoute() {
        lifecycleScope.launch {
            val waypoints = map.getWaypoints()
            if (waypoints.size < 2) {
                // 최소 2개 포인트 필요
                return@launch
            }

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

    // 내비게이션 시작
    private fun startNavigation() {
        locationTracker.startTracking()
        binding.navigationPanel.visibility = View.VISIBLE
        val waypoints = map.getWaypoints()
        if (waypoints.isNotEmpty()) {
            lifecycleScope.launch {
                Log.d("Navigation", "Setting route with ${waypoints.size} waypoints")
                navigationManager.setRoute(waypoints)
                Log.d("Navigation", "Route set completed")
            }
        }
    }

    // 경로 초기화
    private fun clearRoute() {
        map.clearWaypoints()
        binding.txtTotalDistance.visibility = View.GONE
        binding.navigationPanel.visibility = View.GONE
    }

    // 내비게이션 UI 업데이트
    private fun updateNavigationUI(info: NavigationManager.NavigationInfo) {
        binding.apply {
            txtNextInstruction.text = info.instruction
            txtDistance.text = routeManager.formatDistance(info.distance)  // Double 타입용
            txtRemaining.text = "남은 거리: ${routeManager.formatDistance(info.remainingDistance)}"  // Double 타입용
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
