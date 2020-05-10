/**
 *  The main activity for ArUco detection app.
 *  It displays the camera preview and perform ArUco detection using OpenCV library.
 */

package com.hyeonseung.arucodetection

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.opencv.android.*
import org.opencv.core.Mat
import com.google.android.material.floatingactionbutton.FloatingActionButton


class MainActivity : Activity(), CameraBridgeViewBase.CvCameraViewListener2 {

    /** OpenCV camera view. JavaCamera2View uses android.hardware.camera2 */
    private var mOpenCvCameraView: JavaCamera2View? = null

    /** Camera intrinsic parameter calibration. This is required to estimate the pose for ArUco detection. */
    var mCalibrator: CameraCalibrator? = null

    /** Camera intrinsic matrix. */
    private var mCameraMatrix : Mat? = null

    /** Camera distortion matrix. */
    private var mDistortionCoefficients : Mat? = null

    /** Flag to determine whether to do camera calibration. Set by the capture button. */
    private var doCalibration: Boolean = false


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

        // Initialize the camera calibration button.
        val calibrationButton: FloatingActionButton =findViewById<FloatingActionButton>(R.id.calibration_button)
        calibrationButton.setOnClickListener{
            doCalibration = true
        }
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

    override fun onCameraViewStarted(width: Int, height: Int) {

        mCalibrator = CameraCalibrator(width, height)

        // If the calibration data is available, then set the camera matrix and distortion coefficients.
        if (CalibrationResult.tryLoad(this, mCalibrator!!.getCameraMatrix(), mCalibrator!!.getDistortionCoefficients())) {
            mCalibrator!!.setCalibrated();
            mCameraMatrix = mCalibrator!!.cameraMatrix
            mDistortionCoefficients = mCalibrator!!.distortionCoefficients
        }
    }

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(frame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        // Get the current camera frame as OpenCV Mat object
        val mat = frame.rgba()

        if(!doCalibration) {

            if ( mCameraMatrix!=null && mDistortionCoefficients!=null) {
                // Calibration data is available.
                return Mat(
                    findArUCo(
                        mat.nativeObjAddr,
                        mCameraMatrix!!.nativeObjAddr, mDistortionCoefficients!!.nativeObjAddr, true
                    )
                )
            }else{
                // Calibration data is not available.
                return Mat(
                    findArUCo(
                        mat.nativeObjAddr,
                        0,0, false
                    )
                )
            }
        }else{
            // Perform the calibration.
            var patternFound : Boolean? = mCalibrator?.processFrame(frame.gray(), frame.rgba() )

            if (patternFound!!) {
                mCalibrator?.addCorners()
                mCalibrator?.calibrate()

                mCameraMatrix = mCalibrator?.cameraMatrix
                mDistortionCoefficients = mCalibrator?.distortionCoefficients

                // Save the calibration data using SharedPreferences.
                CalibrationResult.save(this,  mCalibrator!!.getCameraMatrix(), mCalibrator!!.getDistortionCoefficients())
            }else{
                Toast.makeText(applicationContext,"The calibration pattern was not found.",Toast.LENGTH_SHORT).show()
            }
            // Do calibration only for the current frame.
            doCalibration = false
            return mat
        }

    }

    // OpenCV JNI call.
    private external fun findArUCo(matAddr: Long, cameraMatrix: Long, distortionCoefficients: Long, isCalibrationAvailable: Boolean): Long


    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 1
    }
}