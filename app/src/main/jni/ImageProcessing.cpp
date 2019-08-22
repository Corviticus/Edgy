
#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include "ImageProcessing.h"

using namespace std;
using namespace cv;

/**
 * A brief program to do some edge detection using the [OpenCV] library
 *
 * The image is first converted to grayscale and then blurred with a simple guassian kernel.
 * After blurring  a 'Canny' Edge detection is performed and the image is converted
 * to BGRA for display back in the app
 *
 * @param env
 * @param thisObject
 * @param width
 * @param height
 * @param lowThreshold
 * @param NV21FrameData
 * @param outPixels
 */
extern "C"
bool
Java_com_software_corvidae_edgy_MainActivity_ImageProcessing (
        JNIEnv* env,
        jobject thisObject,
        const jint width,
        const jint height,
        const int lowThreshold,
        jbyteArray NV21FrameData,
        jintArray outPixels) {

    int ratio = 3;

    /// Original image and grayscale copy
    jbyte* pNV21FrameData = env->GetByteArrayElements(NV21FrameData, nullptr);
    Mat gray_image(height, width, CV_8UC1, (unsigned char *)pNV21FrameData);

    /// Final Result image
    jint* poutPixels = env->GetIntArrayElements(outPixels, nullptr);
    Mat finalImage(height, width, CV_8UC4, (unsigned char *)poutPixels);

    /// Reduce noise with a 3x3 kernel
    Mat blurred;
    GaussianBlur(gray_image, blurred, Size(3, 3), 0);

    /// create new cv::Mat, canny it and convert
    Mat cannyMat(height, width, CV_8UC1);
    Canny(blurred, cannyMat, lowThreshold, lowThreshold * ratio, 3);
    cvtColor(cannyMat, finalImage, COLOR_GRAY2BGRA);

    /// cleanup
    env->ReleaseByteArrayElements(NV21FrameData, pNV21FrameData, 0);
    env->ReleaseIntArrayElements(outPixels, poutPixels, 0);

    return true;
}

