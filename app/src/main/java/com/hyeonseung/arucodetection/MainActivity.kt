package com.hyeonseung.arucodetection

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.opencv.android.*
import org.opencv.android.Utils.matToBitmap
import org.opencv.core.Mat

//class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2{
    class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {

        private var mOpenCvCameraView: JavaCamera2View? = null

        private val mLoaderCallback = object : BaseLoaderCallback(this) {
            override fun onManagerConnected(status: Int) {
                when (status) {
                    LoaderCallbackInterface.SUCCESS -> {
                        Log.i(TAG, "OpenCV loaded successfully")

                        // Load native library after(!) OpenCV initialization
                        System.loadLibrary("native-lib")

                        mOpenCvCameraView!!.enableView()
                    }
                    else -> {
                        super.onManagerConnected(status)
                    }
                }
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            Log.i(TAG, "called onCreate")
            super.onCreate(savedInstanceState)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Permissions for Android 6+
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setContentView(R.layout.activity_main)

            mOpenCvCameraView = findViewById<JavaCamera2View>(R.id.camera_view)

            mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE

            mOpenCvCameraView!!.setCvCameraViewListener(this)
        }


        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        ) {
            when (requestCode) {
                CAMERA_PERMISSION_REQUEST -> {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        mOpenCvCameraView!!.setCameraPermissionGranted()
                    } else {
                        val message = "Camera permission was not granted"
                        Log.e(TAG, message)
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
                else -> {
                    Log.e(TAG, "Unexpected permission request")
                }
            }
        }

        override fun onPause() {
            super.onPause()
            if (mOpenCvCameraView != null)
                mOpenCvCameraView!!.disableView()
        }

        override fun onResume() {
            super.onResume()
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
            } else {
                Log.d(TAG, "OpenCV library found inside package. Using it!")
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            if (mOpenCvCameraView != null)
                mOpenCvCameraView!!.disableView()
        }

        override fun onCameraViewStarted(width: Int, height: Int) {}

        override fun onCameraViewStopped() {}

        override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
            // get current camera frame as OpenCV Mat object
            val mat = frame.rgba()

            val resMat = Mat(findArUCo(mat.nativeObjAddr))
            return resMat
        }

        private external fun findArUCo(matAddr: Long): Long

        companion object {

            private const val TAG = "MainActivity"
            private const val CAMERA_PERMISSION_REQUEST = 1
        }
    }