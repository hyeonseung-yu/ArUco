#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <opencv2/aruco.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/core/core.hpp>
#include <android/log.h>

using namespace std;
using namespace cv;

/**
 * ArUCo detection code used by MainActivity.
 */
extern "C"
JNIEXPORT jlong JNICALL
Java_com_hyeonseung_arucodetection_MainActivity_findArUCo(JNIEnv *env, jobject type,jlong matAddr, jlong cameraMat, jlong distortionCoeffsMat) {

    // Get Mat data for image input and camera calibration matrices.
    Mat &input_mat = *(Mat *) matAddr;
    Mat &cameraMatrix = *(Mat *) cameraMat;
    Mat &distCoeffs = *(Mat *) distortionCoeffsMat;

    // ArUco library requires CV_8UC3 (without alpha channel) input.
    cv::Size input_size = input_mat.size();
    cv::Mat *mat_dst = new cv::Mat(input_size.height, input_size.width, CV_8UC3);
    cv::cvtColor(input_mat, *mat_dst, cv::COLOR_RGBA2RGB);

    // Initialization for ArUco library functions.
    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f>> corners;
    cv::Ptr<cv::aruco::Dictionary> dictionary = aruco::Dictionary::get(aruco::DICT_6X6_250);
    cv::aruco::detectMarkers(*mat_dst, dictionary, corners, ids);

    // If at least one marker detected.
    if (ids.size() > 0) {
        // Draw the indicators around the detected markers.
        cv::aruco::drawDetectedMarkers(*mat_dst, corners, ids);

        // Initialize the pose estimation vectors.
        std::vector<cv::Vec3d> rvecs, tvecs;
        // Estimate the pose.
        cv::aruco::estimatePoseSingleMarkers(corners, 0.05, cameraMatrix, distCoeffs, rvecs, tvecs);
        // Draw axis for each marker.
        for(int i=0; i<ids.size(); i++)
            cv::aruco::drawAxis(*mat_dst, cameraMatrix, distCoeffs, rvecs[i], tvecs[i], 0.1);
    }

    return (jlong)mat_dst;
}