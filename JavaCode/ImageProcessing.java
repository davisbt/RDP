
package com.dji.FPVDemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.Toast;

import com.dji.FPVDemo.Color;
import com.dji.FPVDemo.MyPoint;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.opencv.imgproc.Imgproc.MORPH_CLOSE;
import static org.opencv.imgproc.Imgproc.MORPH_ELLIPSE;
import static org.opencv.imgproc.Imgproc.MORPH_OPEN;

public class ImageProcessing {
    private Mat src;
    private Mat dirt;
    private Mat thresh;
    public Mat maskRed, maskBlue;
    public Bitmap bm;
    private boolean isGray;
    private Context context;

    public ImageProcessing(Context context, String path) {
        OpenCVLoader.initDebug();
        this.context = context;
        try {
            Bitmap imageFromPath = BitmapFactory.decodeFile(path);
            src = new Mat(imageFromPath.getHeight(), imageFromPath.getWidth(), CvType.CV_8UC3);
            Utils.bitmapToMat(imageFromPath, src);
            //Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGB);
        } catch (Exception e) {
            e.printStackTrace();
        }
        dirt = new Mat();
        thresh = new Mat();
        maskRed = new Mat();
        maskBlue = new Mat();
        isGray = false;
        updateBitmap(src);
    }

    public void toGrayOrColor() {

        if (isGray) {
            dirt = src.clone();//new Mat(bm.getWidth(), bm.getHeight(), CvType.CV_8UC3);
            isGray = false;
        } else {
            dirt = new Mat(bm.getWidth(), bm.getHeight(), CvType.CV_8UC1);
            Utils.bitmapToMat(bm, dirt);
            Imgproc.cvtColor(dirt, dirt, Imgproc.COLOR_RGB2GRAY);
            //Toast.makeText(context, "To Gray",Toast.LENGTH_SHORT).show();
            isGray = true;
        }
        updateBitmap(dirt);
    }

    public void blur() {
        dirt = new Mat();
        Utils.bitmapToMat(bm, dirt);
        Imgproc.blur(dirt, dirt, new Size(3, 3));
        Imgproc.GaussianBlur(dirt, dirt, new Size(3, 3), 0);
        updateBitmap(dirt);
    }

    public MyPoint[][] colorThreshold() {
        Mat raw = new Mat();
        Mat hsv = new Mat();
        Utils.bitmapToMat(bm, raw);
        Imgproc.cvtColor(raw, hsv, Imgproc.COLOR_RGB2HSV);
        Mat mask1 = new Mat(), mask2 = new Mat();
        Mat maskGreen = new Mat();
        //=========================RED THRESHOLD===========================================
        double H_MIN_UPPER_RED = 166, S_MIN_UPPER_RED = 95, V_MIN_UPPER_RED = 91, H_MAX_UPPER_RED = 180, S_MAX_UPPER_RED = 215, V_MAX_UPPER_RED = 255;
        double H_MIN_LOWER_RED = 0, S_MIN_LOWER_RED = 70, V_MIN_LOWER_RED = 100, H_MAX_LOWER_RED = 12, S_MAX_LOWER_RED = 230, V_MAX_LOWER_RED = 255;
        Core.inRange(hsv, new Scalar(H_MIN_UPPER_RED, S_MIN_UPPER_RED, V_MIN_UPPER_RED), new Scalar(H_MAX_UPPER_RED, S_MAX_UPPER_RED, V_MAX_UPPER_RED), mask1);
        Core.inRange(hsv, new Scalar(H_MIN_LOWER_RED, S_MIN_LOWER_RED, V_MIN_LOWER_RED), new Scalar(H_MAX_LOWER_RED, S_MAX_LOWER_RED, V_MAX_LOWER_RED), mask2);
        Core.bitwise_or(mask1, mask2, maskRed);
        Imgproc.GaussianBlur(maskRed, maskRed, new Size(3, 3), 0);
        Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5)));
        Point[][] redCircles = circles(maskRed);
        //=========================RED THRESHOLD===========================================

        //=========================GREEN THRESHOLD=========================================
        double H_MIN_GREEN = 78, S_MIN_GREEN = 180, V_MIN_GREEN = 70, H_MAX_GREEN = 87, S_MAX_GREEN = 235, V_MAX_GREEN = 227;
        Core.inRange(hsv, new Scalar(H_MIN_GREEN, S_MIN_GREEN, V_MIN_GREEN), new Scalar(H_MAX_GREEN, S_MAX_GREEN, V_MAX_GREEN), maskGreen);
        Imgproc.GaussianBlur(maskGreen, maskGreen, new Size(3, 3), 0);
        Imgproc.morphologyEx(maskGreen, maskGreen, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5)));
        Point[][] greenCircles = circles(maskGreen);
        //=========================GREEN THRESHOLD=========================================

        //=========================BLUE THRESHOLD==========================================
        double H_MIN_BLUE = 90, S_MIN_BLUE = 113, V_MIN_BLUE = 111, H_MAX_BLUE = 100, S_MAX_BLUE = 255, V_MAX_BLUE = 255;
        Core.inRange(hsv, new Scalar(H_MIN_BLUE, S_MIN_BLUE, V_MIN_BLUE), new Scalar(H_MAX_BLUE, S_MAX_BLUE, V_MAX_BLUE), maskBlue);
        Imgproc.GaussianBlur(maskBlue, maskBlue, new Size(3, 3), 0);
        Imgproc.morphologyEx(maskBlue, maskBlue, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5)));
        Point[][] blueCircles = circles(maskBlue);
        //=========================BLUE THRESHOLD==========================================

        //Join the red green and blue circles.
        Core.bitwise_or(maskRed, maskBlue, thresh);
        Core.bitwise_or(maskGreen, thresh, thresh);
        ArrayList<MyPoint> points = new ArrayList<>();
        for (Point[] row : redCircles) {
            for (Point redC : row) {
                points.add(new MyPoint(redC.x, redC.y, Color.RED));
            }
        }
        for (Point[] row : greenCircles) {
            for (Point greenC : row) {
                points.add(new MyPoint(greenC.x, greenC.y, Color.GREEN));
            }
        }
        for (Point[] row : blueCircles) {
            for (Point blueC : row) {
                points.add(new MyPoint(blueC.x, blueC.y, Color.BLUE));
            }
        }
        MyPoint[][] centersOrdered = orderCirclesToMatrix(orderCircles(points.toArray(new MyPoint[points.size()])));
        //If we still didn't finish with the blue ones, enter the remaining
        String ss = "";
        for (int i = 0; i < centersOrdered.length; i++) {
            for (int j = 0; j < centersOrdered[i].length; j++) {
                ss += "Circle " + (i * centersOrdered.length + j) + " (" + i + ", " + j + ") " + ": " + centersOrdered[i][j].x + ", " + centersOrdered[i][j].y + " is " + centersOrdered[i][j].color.name() + "\n";
            }
        }
        Toast.makeText(context, ss, Toast.LENGTH_LONG).show();
        updateBitmap(thresh);
        return centersOrdered;
    }

    private boolean pointInArray(Point p, Point[][] arr) {
        for (Point[] rows : arr) {
            for (Point point : rows) {
                if (p.x == point.x && p.y == point.y) {
                    return true;
                }
            }
        }
        return false;
    }

    public void contours() {

        Mat image = new Mat(), temp = new Mat();
        Utils.bitmapToMat(bm, temp);
        Imgproc.cvtColor(temp, image, Imgproc.COLOR_RGB2GRAY);

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        Imgproc.findContours(image, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat copySrc = src.clone();
        double[] radiuses = new double[contours.size()];
        for (int i = 0; i < contours.size(); i++) {
            Point center = new Point();
            float[] radius = new float[1];
            MatOfPoint c = contours.get(i);
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            Imgproc.minEnclosingCircle(c2f, center, radius);
            radiuses[i] = radius[0];
        }
        double stdDevRadius = stdDev(radiuses), avgRadius = averageOf(radiuses[0]);
        for (int i = 0; i < contours.size(); i++) {
            Point center = new Point();
            float[] radius = new float[1];
            MatOfPoint c = contours.get(i);
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            Imgproc.minEnclosingCircle(c2f, center, radius);
            //Check if the circle is of a "Normal" size.
            if (inStd(stdDevRadius, avgRadius, radius[0], 1)) {
                Imgproc.circle(copySrc, center, (int) radius[0], new Scalar(255, 0, 0), 2);
            }
        }
        updateBitmap(copySrc);
    }

    public void threshGreen() {
        Mat green = new Mat();
        Mat raw = new Mat();
        Mat hsv = new Mat();
        Utils.bitmapToMat(bm, raw);
        Imgproc.cvtColor(raw, hsv, Imgproc.COLOR_RGB2HSV);
        double H_MIN_BLUE = 78, S_MIN_BLUE = 180, V_MIN_BLUE = 70, H_MAX_BLUE = 87, S_MAX_BLUE = 235, V_MAX_BLUE = 227;
        Core.inRange(hsv, new Scalar(H_MIN_BLUE, S_MIN_BLUE, V_MIN_BLUE), new Scalar(H_MAX_BLUE, S_MAX_BLUE, V_MAX_BLUE), green);
        Imgproc.GaussianBlur(green, green, new Size(3, 3), 0);
        Imgproc.morphologyEx(green, green, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5)));
        updateBitmap(green);
    }

    public Point[][] circles(Mat binaryImage) {
        Mat circles = new Mat(), currBinImg = binaryImage != null ? binaryImage : thresh;
        /**
         * minDist: Minimum distance between the center (x, y) coordinates of detected circles. If the minDist is too small, multiple circles in the same neighborhood as the original may be (falsely) detected. If the minDist is too large, then some circles may not be detected at all.
         *
         * param1: Gradient value used to handle edge detection in the Yuen et al. method.
         *
         * param2: Accumulator threshold value for the cv2.HOUGH_GRADIENT method. The smaller the threshold is, the more circles will be detected (including false circles). The larger the threshold is, the more circles will potentially be returned.
         *
         * minRadius: Minimum size of the radius (in pixels).
         *
         * maxRadius: Maximum size of the radius (in pixels).
         */
        //Imgproc.HoughCircles(thresh, circles, Imgproc.CV_HOUGH_GRADIENT, 2, 30, 100, 70, 1, 80);
        //Imgproc.HoughCircles(thresh, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 95, 100, 10, 50, 50);

        boolean smallStickers = false;
        Imgproc.morphologyEx(currBinImg, currBinImg, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(13, 13)));
        Imgproc.GaussianBlur(currBinImg, currBinImg, new Size(5, 5), 0);
        Imgproc.morphologyEx(currBinImg, currBinImg, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(7, 7)));

        //Small Stickers
        if (smallStickers)
            Imgproc.HoughCircles(currBinImg, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 50, 100, 10, 15, 30);
        else//Big red paper circles
            Imgproc.HoughCircles(currBinImg, circles, Imgproc.CV_HOUGH_GRADIENT, 1, 50, 100, 10, 70, 90);

        Point[][] centersOrdered = orderCirclesToMatrix(orderCircles(circles));

        //===================Only if we are working with thresh Mat (Called from MainActivity)===========
        if (binaryImage == null) {
            Mat copySrc = src.clone();
            for (int i = 0; i < circles.cols(); i++) {
                double[] vCircle = circles.get(0, i);
                Point pt = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
                int radius = (int) Math.round(vCircle[2]);
                Imgproc.circle(copySrc, pt, radius, new Scalar(50, 255, 0), 5);
            }

            String ss = "";
            for (int i = 0; i < centersOrdered.length; i++) {
                for (int j = 0; j < centersOrdered[i].length; j++) {
                    Imgproc.putText(copySrc, "" + i, centersOrdered[i][j], Core.FONT_HERSHEY_PLAIN, 7.0, new Scalar(0, 255, 255));//circle(copySrc, p, );
                    ss += "Circle " + (i * centersOrdered.length + j) + " (" + i + ", " + j + ") " + ": " + centersOrdered[i][j].x + ", " + centersOrdered[i][j].y + "\n";
                }
            }
            Toast.makeText(context, ss, Toast.LENGTH_LONG).show();
            updateBitmap(copySrc);
        }
        //===================Only if we are working with thresh Mat (Called from MainActivity)===========

        return centersOrdered;
    }

    public void makeROI() {
        Mat image = new Mat(), temp = new Mat();
        Utils.bitmapToMat(bm, temp);
        Imgproc.cvtColor(temp, image, Imgproc.COLOR_RGB2GRAY);

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        Imgproc.findContours(image, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat copySrc = src.clone();
        double maxArea = 0;
        float[] radius = new float[1];
        Point center = new Point();
        for (int i = 0; i < contours.size(); i++) {
            MatOfPoint c = contours.get(i);
            if (Imgproc.contourArea(c) > maxArea) {
                MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
                Imgproc.minEnclosingCircle(c2f, center, radius);
            }
        }
        Imgproc.circle(copySrc, center, (int) radius[0], new Scalar(255, 0, 0), 2);


        updateBitmap(copySrc);
    }

    public static double stdDev(double[] arr) {
        double average = averageOf(arr), variance = 0;
        for (int i = 0; i < arr.length; i++) {
            variance += Math.pow(arr[i] - average, 2);
        }
        variance /= arr.length;
        double stdDeviation = Math.sqrt(variance);
        return stdDeviation;
    }


    public static double averageOf(double... arr) {
        double average = 0;
        for (int i = 0; i < arr.length; i++) {
            average += arr[i];
        }
        average /= arr.length;
        return average;
    }

    public static boolean inStd(double std, double avr, double n, int mul) {
        return n <= avr + std * mul && n >= avr - std * mul;
    }

    public double[][] filterCirclesOldOne(Mat circles) {
        double average = 0, variance = 0;
        for (int i = 0; i < circles.cols(); i++) {
            double[] vCircle = circles.get(0, i);
            Point pt = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
            int radius = (int) Math.round(vCircle[2]);
            average += radius;
        }
        average /= circles.cols();
        for (int i = 0; i < circles.cols(); i++) {
            double[] vCircle = circles.get(0, i);
            Point pt = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
            int radius = (int) Math.round(vCircle[2]);
            variance += Math.pow(radius - average, 2);
        }
        variance /= circles.cols();
        double whichStd = 0.7;
        double stdDeviation = Math.sqrt(variance);
        ArrayList<double[]> filteredCir = new ArrayList<>();
        for (int i = 0; i < circles.cols(); i++) {
            double[] vCircle = circles.get(0, i);
            Point pt = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
            int radius = (int) Math.round(vCircle[2]);
            if (inRange(radius, average - whichStd * stdDeviation, average + whichStd * stdDeviation))
                filteredCir.add(vCircle);
        }
        double[][] filteredCircles = filteredCir.toArray(new double[filteredCir.size()][]);
        return filteredCircles;
    }

    public double[][] filterCircles(Mat circles) {
        for (int i = 0; i < circles.cols(); i++) {
            double[] circle1 = circles.get(0, i);
            Point pt1 = new Point(Math.round(circle1[0]), Math.round(circle1[1]));
            for (int j = 0; j < circles.cols(); j++) {
                double[] circle2 = circles.get(0, j);
                Point pt2 = new Point(Math.round(circle2[0]), Math.round(circle2[1]));
                //TODO: Calculate distance from all circles. If the distance is bigger than the stdDev of distance, then it means that it is a fake circle.
            }
        }

        return null;
    }

    public boolean inRange(double num, double lower, double upper) {
        return num <= upper && num >= lower;
    }

    public void findingOptimalParameters(int numCircles) {
        Mat temp = new Mat(bm.getWidth(), bm.getHeight(), CvType.CV_8UC1), temppp = new Mat();

        Utils.bitmapToMat(bm, temppp);
        Imgproc.cvtColor(temppp, temp, Imgproc.COLOR_RGB2GRAY);

        Mat circles = new Mat();
        Imgproc.HoughCircles(temp, circles, Imgproc.CV_HOUGH_GRADIENT, 2, 100, 100, 100, 30, 400);
        //Make iterative function to find the optimal param2 of function.

        int currentNumCircles = circles.cols();


        while (currentNumCircles < numCircles) {
            Imgproc.HoughCircles(temp, circles, Imgproc.CV_HOUGH_GRADIENT, 2, 100, 100, 100, 30, 400);

        }

    }

    //Orders circles by their center and convert them to a 2d array, according to thresh. If x from circle is like x from previous circle +- thresh, then it is
    //in the same column, otherwise it is in the next column.
    public Point[] orderCircles(Mat circles) {
        Point[] centersOfCircles = new Point[circles.cols()];
        for (int i = 0; i < circles.cols(); i++) {
            double[] vCircle = circles.get(0, i);
            centersOfCircles[i] = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
        }
        Arrays.sort(centersOfCircles, new Comparator<Point>() {
            public int compare(Point a, Point b) {
                //=================Important to adapt thresh according to height of picture made by drone=============
                int thresh = 80;
                int xComp = Double.compare(a.x, b.x);
                //if they are on the same column, check who is the higher one.
                if (Math.abs(a.x - b.x) <= thresh) {
                    return -Double.compare(a.y, b.y);
                } else
                    return xComp;
            }
        });
        return centersOfCircles;
    }

    public MyPoint[] orderCircles(MyPoint[] circles) {
        Arrays.sort(circles, new MyPoint());
        return circles;
    }

    public Point[][] orderCirclesToMatrix(Point[] orderedCircles) {
        ArrayList<ArrayList<Point>> matrixCircles = new ArrayList<>();
        matrixCircles.add(new ArrayList<Point>());
        int currentIndex = 0;
        matrixCircles.get(currentIndex).add(orderedCircles[0]);
        for (int i = 1; i < orderedCircles.length; i++) {
            Point currentP = orderedCircles[i], lastP = orderedCircles[i - 1];
            if (currentP.y < lastP.y) {
                matrixCircles.get(currentIndex).add(currentP);
            } else {
                matrixCircles.add(new ArrayList<Point>());
                matrixCircles.get(++currentIndex).add(currentP);
            }
        }
        return arrayListsToArrays(matrixCircles);
    }

    public MyPoint[][] orderCirclesToMatrix(MyPoint[] orderedCircles) {
        ArrayList<ArrayList<MyPoint>> matrixCircles = new ArrayList<>();
        matrixCircles.add(new ArrayList<MyPoint>());
        int currentIndex = 0;
        matrixCircles.get(currentIndex).add(orderedCircles[0]);
        for (int i = 1; i < orderedCircles.length; i++) {
            MyPoint currentP = orderedCircles[i], lastP = orderedCircles[i - 1];
            if (currentP.y < lastP.y) {
                matrixCircles.get(currentIndex).add(currentP);
            } else {
                matrixCircles.add(new ArrayList<MyPoint>());
                matrixCircles.get(++currentIndex).add(currentP);
            }
        }
        return arrayListsToArraysMP(matrixCircles);
    }

    private MyPoint[][] arrayListsToArraysMP(ArrayList<ArrayList<MyPoint>> arrayLists) {
        MyPoint[][] array = new MyPoint[arrayLists.size()][];
        for (int i = 0; i < arrayLists.size(); i++) {
            ArrayList<MyPoint> row = arrayLists.get(i);
            array[i] = row.toArray(new MyPoint[row.size()]);
        }
        return array;
    }

    private Point[][] arrayListsToArrays(ArrayList<ArrayList<Point>> arrayLists) {
        Point[][] array = new Point[arrayLists.size()][];
        for (int i = 0; i < arrayLists.size(); i++) {
            ArrayList<Point> row = arrayLists.get(i);
            array[i] = row.toArray(new Point[row.size()]);
        }
        return array;
    }

    public void closing() {
        Mat temp = new Mat();
        Utils.bitmapToMat(bm, temp);
        Imgproc.morphologyEx(temp, temp, MORPH_CLOSE, Imgproc.getStructuringElement(MORPH_ELLIPSE, new Size(7, 7)));
        updateBitmap(temp);
    }

    public void opening() {
        Mat temp = new Mat();
        Utils.bitmapToMat(bm, temp);
        Imgproc.morphologyEx(temp, temp, MORPH_OPEN, Imgproc.getStructuringElement(MORPH_ELLIPSE, new Size(3, 3)));
        updateBitmap(temp);
    }

    private void updateBitmap(Mat image) {
        bm = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, bm);
    }
}