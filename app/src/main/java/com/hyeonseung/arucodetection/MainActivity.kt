package com.hyeonseung.arucodetection

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.*
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class MainActivity : AppCompatActivity(), CvCameraViewListener2{

    private lateinit var  mOpenCvCameraView: JavaCameraView
    private lateinit var mRGBA:Mat
    private lateinit var mRGBAT:Mat

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i( TAG, "OpenCV loaded successfully" )
                    mOpenCvCameraView.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "called onCreate")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        mOpenCvCameraView = findViewById(R.id.camera_view)

        mOpenCvCameraView.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView.setCvCameraViewListener(this)
    }

    override fun onPause() {
        super.onPause()
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView()
    }

    override fun onResume() {
        super.onResume()
        if (OpenCVLoader.initDebug())
        {
            Log.i(TAG, "Open CV is configured succesfully")
            mLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS)

        }else{
            Log.i(TAG, "Open CV is not working")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mOpenCvCameraView != null) mOpenCvCameraView!!.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        mRGBA = Mat(height, width, CvType.CV_8UC4)
    }
    override fun onCameraViewStopped() {
        mRGBA.release()
    }
    override fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat {
        mRGBA = inputFrame.rgba()
        mRGBAT = mRGBA.t()
        Core.flip(mRGBA.t(), mRGBAT, 1)
        Imgproc.resize(mRGBA, mRGBAT, mRGBA.size())
        return return mRGBAT
    }

    override fun onPointerCaptureChanged(hasCapture: Boolean) {
        super.onPointerCaptureChanged(hasCapture)
    }


    companion object {
        private const val TAG = "MainActivity"
    }

    init {
        Log.i(TAG, "Instantiated new " + this.javaClass)
        /*if (OpenCVLoader.initDebug())
        {
            Log.i(TAG, "Open CV is configured succesfully")

        }else{
            Log.i(TAG, "Open CV is not working")
        }*/
    }
}