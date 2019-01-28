
#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include "ImageProcessing.h"

using namespace std;
using namespace cv;

extern "C"
bool
Java_com_software_corvidae_edgy_MainActivity_ImageProcessing (
        JNIEnv* env,
        const jobject thiz,
        const jint width,
        const jint height,
        const int lowThreshold,
        const jbyteArray NV21FrameData,
        jintArray outPixels) {

    int ratio = 3;

    /// Original image and grayscale copy
    jbyte* pNV21FrameData = env->GetByteArrayElements(NV21FrameData, 0);
    Mat gray_image(height, width, CV_8UC1, (unsigned char *)pNV21FrameData);

    /// Final Result image
    jint* poutPixels = env->GetIntArrayElements(outPixels, 0);
    Mat finalImage(height, width, CV_8UC4, (unsigned char *)poutPixels);

    /// Reduce noise with a 3x3 kernel
    Mat blurred;
    GaussianBlur(gray_image, blurred, Size(3, 3), 0);

    /// create new cv::Mat, canny it and convert
    Mat cannyMat(height, width, CV_8UC1);
    Canny(blurred, cannyMat, lowThreshold, lowThreshold * ratio, 3);
    cvtColor(cannyMat, finalImage, CV_GRAY2BGRA);

    /// cleanup
    env->ReleaseByteArrayElements(NV21FrameData, pNV21FrameData, 0);
    env->ReleaseIntArrayElements(outPixels, poutPixels, 0);

    return true;
}

