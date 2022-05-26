package com.first.ridingpartnerinsiheung.scenarios.main.maps.ridingMap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.location.LocationManager
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.first.ridingpartnerinsiheung.R
import com.first.ridingpartnerinsiheung.databinding.FragmentRidingBinding
import com.first.ridingpartnerinsiheung.extensions.showToast
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.LocationTrackingMode

import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.FusedLocationSource
import kotlinx.coroutines.flow.collect

class RidingFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mNaverMap: NaverMap

    private var ridingState = false // 라이딩 상태
    private var startLatLng = LatLng(0.0, 0.0) // polyline 시작점
    private var endLatLng = LatLng(0.0, 0.0) // polyline 끝점

    private lateinit var binding: FragmentRidingBinding
    private val viewModel by viewModels<RidingViewModel>()

    private lateinit var locationSource: FusedLocationSource

    private var locationManager: LocationManager? = null

    private var savedTimer = 0
    private var savedSpeed = 0.0
    private var savedDistance = 0.0

    private var startTime = ""
    private var endTime = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        initBinding()

        locationSource = FusedLocationSource(this, PERMISSION_CODE)

        val fm = childFragmentManager
        val mapFragment = fm.findFragmentById(R.id.ridingMapView) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.ridingMapView, it).commit()
            }
        mapFragment?.getMapAsync(this)

        locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        initObserve()

        return binding.root
    }

    private fun initBinding(
        inflater: LayoutInflater = this.layoutInflater,
        container: ViewGroup? = null
    ) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_riding, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
    }
    private fun initObserve() {
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
            viewModel.event.collect { event ->
                when (event) {
                    RidingViewModel.RidingEvent.StartRiding -> startRiding()
                    RidingViewModel.RidingEvent.StopRiding -> stopRiding()
                    RidingViewModel.RidingEvent.SaveRiding -> saveRiding()
                }
            }
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        mNaverMap = naverMap
        mNaverMap.locationSource = locationSource
        mNaverMap.locationTrackingMode = LocationTrackingMode.Follow

        setOverlay()
    }

    private fun setOverlay() {
        mNaverMap.locationTrackingMode = LocationTrackingMode.Follow
        val locationOverlay = mNaverMap.locationOverlay
        locationOverlay.subIcon =
            OverlayImage.fromResource(com.naver.maps.map.R.drawable.navermap_location_overlay_icon)
        locationOverlay.subIconWidth = 80
        locationOverlay.subIconHeight = 40
        locationOverlay.subAnchor = PointF(0.5f, 1f)
    }

    private fun changeLocation() {
        mNaverMap.addOnLocationChangeListener { location ->
            if (mNaverMap.locationTrackingMode == LocationTrackingMode.NoFollow) {
                setOverlay()
            }
            drawPath(LatLng(location.latitude, location.longitude),)
            viewModel.mLocation = location
        }
    }
    private fun startRiding(){ // 시작버튼
        ridingState = true // 라이딩중
        binding.startBtn.visibility = View.GONE
        binding.stopBtn.visibility = View.VISIBLE
        binding.saveBtn.visibility = View.GONE
        startTime = viewModel.getTimeNow() // 시작 시감

        locationSource.lastLocation?.let { location ->
            startLatLng =
                LatLng(location.latitude, location.longitude)
            var startMarker = marker(startLatLng, "출발지점") // 출발지점 마크
        }
        changeLocation()

        setOverlay()

        viewModel.befLatLng = startLatLng
        viewModel.calDisSpeed() // 속도, 거리, 주행시간 계산 시작
    }
    private fun stopRiding() { // 중지버튼
        ridingState = false
        binding.startBtn.visibility = View.VISIBLE
        binding.startBtn.text = "이어서 라이딩하기"
        binding.saveBtn.visibility = View.VISIBLE
        binding.stopBtn.visibility = View.GONE

        endTime = viewModel.getTimeNow()

        locationSource.lastLocation?.let { location ->
            viewModel.stopCal()

            savedSpeed = viewModel.averSpeed.value // 평균속도 받아오기
            savedTimer = viewModel.timer.value // 총 주행시간 받아오기
            savedDistance = viewModel.sumDistance.value // 총 주행거리 받아오기

            endLatLng =
                LatLng(location.latitude, location.longitude)
            var endMarker = marker(endLatLng, "도착 지점")
        }
    }
    private fun saveRiding(){
        // 페이지 이동
    }
    private fun drawPath(latLng:LatLng) {
        endLatLng = LatLng(latLng.latitude, latLng.longitude)
        viewModel.calDisSpeed()

        val path = PathOverlay()
        path.coords = listOf(startLatLng, endLatLng)
        path.color = Color.BLUE
        path.map = mNaverMap

        startLatLng = endLatLng
    }

    // 시작지점 마크
    private fun marker(
        latLng: LatLng,
        title: String
    ): Marker {
        val marker = Marker()
        marker.position = latLng
        marker.map = mNaverMap
        marker.width = 50
        marker.height = 50
        marker.captionText = title
        return marker
    }

    override fun onResume() {
        super.onResume()
        requirePermissions()
    }

    //  권한 요청
    private val PERMISSION_CODE = 100

    private fun requirePermissions(){
        val permissions=arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        val isAllPermissionsGranted = permissions.all { //  permissions의 모든 권한 체크
            ActivityCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (isAllPermissionsGranted) {    //  모든 권한이 허용되어 있을 경우
            //permissionGranted()
        } else { //  그렇지 않을 경우 권한 요청
            ActivityCompat.requestPermissions(requireActivity(), permissions, PERMISSION_CODE)
        }
    }

    // 권한 요청 완료시 이 함수를 호출해 권한 요청에 대한 결과를 argument로 받을 수 있음
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == PERMISSION_CODE){
            if(grantResults.isNotEmpty()){
                for(grant in grantResults){
                    if(grant == PackageManager.PERMISSION_GRANTED) {
                        /*no-op*/
                    }else{
                        permissionDenied()
                        requirePermissions()
                    }
                }
            }
        }
    }
    // 권한이 없는 경우 실행
    private fun permissionDenied() {
        showToast("위치 권한이 필요합니다")
    }
}