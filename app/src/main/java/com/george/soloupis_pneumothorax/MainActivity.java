package com.george.soloupis_pneumothorax;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ImageReader;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.george.soloupis_pneumothorax.customview.OverlayView;
import com.george.soloupis_pneumothorax.customview.RecyclerViewAdapter;
import com.george.soloupis_pneumothorax.customview.SoloupisEmptyRecyclerView;
import com.george.soloupis_pneumothorax.env.BorderedText;
import com.george.soloupis_pneumothorax.env.ImageUtils;
import com.george.soloupis_pneumothorax.opencv.ColorBlobDetector;
import com.george.soloupis_pneumothorax.tflite.Classifier;
import com.george.soloupis_pneumothorax.tracking.MultiBoxTracker;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends CameraActivity implements ImageReader.OnImageAvailableListener, RecyclerViewAdapter.WheelsClickItemListener {

    //OpenCV library
    static {
        System.loadLibrary("opencv_java4");
    }

    // Configuration values for the prepackaged SSD model.
    private int TF_OD_API_INPUT_SIZE;
    // Minimum detection confidence to track a detection.
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(100, 100);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private boolean computingDetection = false;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;
    private BorderedText borderedText;

    //Tensorflow model
    private TensorFlowInferenceInterface tf;
    private static final String MODEL_ASSETS = "frozen_inference_graph_27000_007.pb";
    private static final String INT_FRAME = "int_frame";
    private String INPUT_NAME = "ImageTensor";
    private String OUTPUT_NAMES = "SemanticPredictions";
    private int[] mIntValues;
    private byte[] mFlatIntValues;
    private int[] mOutputs;

    private ImageView imageOutput, imageWheel;

    //OpenCv
    private Mat mRgba;
    private Scalar mBlobColorRgba;
    private Scalar mBlobColorHsv;
    private ColorBlobDetector mDetector;
    private Mat mSpectrum;
    private Scalar CONTOUR_COLOR;
    private Bitmap bit;

    //Recycler view
    private SoloupisEmptyRecyclerView mRecyclerView;
    private RecyclerViewAdapter mRecyclerViewAdapter;
    private LinearLayoutManager layoutManager;

    //Fab
    private FloatingActionButton fab;

    private FrameLayout frameLayout;
    int heightScreen, widthScreen;


    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation, int size2) {

        TF_OD_API_INPUT_SIZE = size2;

        //Tensorflow
        //initialize tensorflow
        //Initialize inference
        tf = new TensorFlowInferenceInterface(getAssets(), MODEL_ASSETS);

        mIntValues = new int[TF_OD_API_INPUT_SIZE * TF_OD_API_INPUT_SIZE];
        mFlatIntValues = new byte[TF_OD_API_INPUT_SIZE * TF_OD_API_INPUT_SIZE * 3];
        mOutputs = new int[TF_OD_API_INPUT_SIZE * TF_OD_API_INPUT_SIZE];
        imageOutput = findViewById(R.id.imageOutput);
        imageWheel = findViewById(R.id.imageWheel);
        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                cropSize, cropSize,
                sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
        //OpenCv
        mRgba = new Mat(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        //Green color below
        //https://towardsdatascience.com/object-detection-via-color-based-image-segmentation-using-python-e9b7c72f0e11
        /*mBlobColorHsv = new Scalar(60, 255, 255);*/
        //red color
        mBlobColorHsv = new Scalar(255, 0, 0, 255);
        // green color
        CONTOUR_COLOR = new Scalar(60, 255, 255);

        //Recycler view
        mRecyclerView = findViewById(R.id.recyclerViewWheels);
        mRecyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mRecyclerView.setLayoutManager(layoutManager);

        mRecyclerViewAdapter = new RecyclerViewAdapter(this, this);

        //class to view one item at a time at recycler view
        LinearSnapHelper linearSnapHelper = new SnapHelperOneByOne();
        linearSnapHelper.attachToRecyclerView(mRecyclerView);

        //Screen metrics
        widthScreen = getResources().getDimensionPixelSize(R.dimen.frameLayoutWidth);
        heightScreen = getResources().getDimensionPixelSize(R.dimen.frameLayoutHeight);

    }

    @Override
    protected void processImage() {

        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }

        computingDetection = true;
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        //Normalize the pixels
                        croppedBitmap.getPixels(mIntValues, 0, croppedBitmap.getWidth(), 0, 0,
                                croppedBitmap.getWidth(), croppedBitmap.getHeight());
                        Log.e("MINTVALUES", mIntValues.length + " -> "+ Arrays.toString(mIntValues));
                        for (int i = 0; i < mIntValues.length; ++i) {
                            final int val = mIntValues[i];
                            mFlatIntValues[i * 3 + 0] = (byte) ((val >> 16) & 0xFF);
                            mFlatIntValues[i * 3 + 1] = (byte) ((val >> 8) & 0xFF);
                            mFlatIntValues[i * 3 + 2] = (byte) (val & 0xFF);
                        }

                        Log.e("FLOAT_VALUES", mFlatIntValues[0] + ", " +mFlatIntValues[1] + ", " +mFlatIntValues[2]);


                        //feed,run,fetch
                        tf.feed(INPUT_NAME, mFlatIntValues, 1, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, 3);
                        tf.run(new String[]{OUTPUT_NAMES});
                        tf.fetch(OUTPUT_NAMES, mOutputs);

                        final Bitmap output = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
                        for (int y = 0; y < TF_OD_API_INPUT_SIZE; y++) {
                            for (int x = 0; x < TF_OD_API_INPUT_SIZE; x++) {
                                output.setPixel(x, y, mOutputs[y * TF_OD_API_INPUT_SIZE + x] == 0 ? Color.TRANSPARENT : Color.GREEN);
                            }
                        }

                        trackingOverlay.postInvalidate();
                        computingDetection = false;
                        //OpenCV
                        mDetector.setHsvColor(mBlobColorHsv);
                        Mat mat = new Mat();

                        //BEFORE OPEN_CV.....with Porterduf mode
                        /*Bitmap bmp32 = ImageUtils.cropBitmapWithMask(croppedBitmap, output).copy(Bitmap.Config.ARGB_8888, true);
                        imageWheel.setImageBitmap(bmp32);*/

                        //WITH OPEN_CV
                        /*Bitmap bmp32 = output.copy(Bitmap.Config.ARGB_8888, true);
                        Utils.bitmapToMat(bmp32, mat);*/

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        //pneumothorax with open_cv
                                        /*imageWheel.setImageBitmap(ImageUtils.getResizedBitmap(onCameraFrame(mat), widthScreen, heightScreen));*/

                                        //pneumothorax with porterduff
                                        /*Bitmap bmp32 = ImageUtils.cropBitmapWithMask(croppedBitmap, output).copy(Bitmap.Config.ARGB_8888, true);*/
                                        imageWheel.setImageBitmap(ImageUtils.getResizedBitmap(output, widthScreen, heightScreen));;
                                    }
                                });
                    }
                });
    }

    //Below code finds contours and center of blobs
    public Bitmap onCameraFrame(Mat inputFrame) {
        mRgba = inputFrame;
        mDetector.process(mRgba);

        //Detect contours
        List<MatOfPoint> contours = mDetector.getContours();
        Log.i("Contours count: ", String.valueOf(contours.size()));
        Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR, 0);

        //Below code finds the center of the contour
        /*List<Moments> mu = new ArrayList<>(contours.size());
        for (int i = 0; i < contours.size(); i++) {
            mu.add(i, Imgproc.moments(contours.get(i), false));
            Moments p = mu.get(i);
            int x = (int) (p.get_m10() / p.get_m00());
            int y = (int) (p.get_m01() / p.get_m00());
            Imgproc.circle(mRgba, new Point(x, y), 4, new Scalar(255, 0, 0, 255));
        }*/

        //Below code draws rectangle over contour
        /*for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++) {
            // Minimum size allowed for consideration
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            MatOfPoint2f contour2f = new MatOfPoint2f(contours.get(contourIdx).toArray());
            //Processing on mMOP2f1 which is in type MatOfPoint2f
            double approxDistance = Imgproc.arcLength(contour2f, true) * 0.002;
            Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);

            //Convert back to MatOfPoint
            MatOfPoint points = new MatOfPoint(approxCurve.toArray());

            // Get bounding rect of contour
            Rect rect = Imgproc.boundingRect(points);
            //draw rectangle
*//*
            Imgproc.rectangle(mRgba, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 0, 0, 0), 0);
*//*
            //Bitmap to mat
            Mat smallMat = new Mat();
            Utils.bitmapToMat(bit, smallMat);

            //Resize mat and copy to mRgba
            //Apply bigger values to cover green mask
            Mat resizeMat = new Mat(rect.height + 4, rect.width + 4, smallMat.type());

            Imgproc.resize(smallMat, resizeMat, resizeMat.size(), 0, 0, Imgproc.INTER_AREA);
            *//*Imgproc.resize(smallMat, resizeMat, resizeMat.size());*//*
            //Some checks in case mat is smaller or larger
            if (rect.x > 0 && rect.y > 0 && resizeMat.width() > 0 && resizeMat.height() > 0) {

                try {
                    *//*Mat submat = mRgba.submat(new Rect(rect.x, rect.y - 2, resizeMat.width(), resizeMat.height()));
                    resizeMat.copyTo(submat);*//*
                } catch (final Exception e) {
                }

            }
        }*/
        Bitmap output = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, output);

        return output;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    //Not needed for pneumothorax
    @Override
    public void onListItemClick(int itemIndex) {

        if (itemIndex == 0) {
            //With m5.png
            try {
                bit = BitmapFactory.decodeStream(getAssets().open("m5.png"));
                /*Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open("m5.png"));
                bit = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);*/
/*
                bit = Bitmap.createBitmap(BitmapFactory.decodeStream(getAssets().open("m5.png")).getWidth(),BitmapFactory.decodeStream(getAssets().open("m5.png")).getHeight(),Bitmap.Config.ARGB_8888);
*/
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        } else if (itemIndex == 1) {
            //With m6.png
            try {
                bit = BitmapFactory.decodeStream(getAssets().open("m6.png"));
                /*Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open("m6.png"));
                bit = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);*/
/*
                bit = Bitmap.createBitmap(BitmapFactory.decodeStream(getAssets().open("m6.png")).getWidth(),BitmapFactory.decodeStream(getAssets().open("m6.png")).getHeight(),Bitmap.Config.ARGB_8888);
*/

            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        } else if (itemIndex == 2) {
            //With m8.png
            try {
                bit = BitmapFactory.decodeStream(getAssets().open("m8.png"));
                /*Bitmap bitmap = BitmapFactory.decodeStream(getAssets().open("m8.png"));
                bit = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);*/
/*
                bit = Bitmap.createBitmap(BitmapFactory.decodeStream(getAssets().open("m8.png")).getWidth(),BitmapFactory.decodeStream(getAssets().open("m8.png")).getHeight(),Bitmap.Config.ARGB_8888);
*/

            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }

    //Not needed for Pneumothorax
    public class SnapHelperOneByOne extends LinearSnapHelper {

        @Override
        public int findTargetSnapPosition(RecyclerView.LayoutManager layoutManager, int velocityX, int velocityY) {

            if (!(layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)) {
                return RecyclerView.NO_POSITION;
            }

            final View currentView = findSnapView(layoutManager);

            if (currentView == null) {
                return RecyclerView.NO_POSITION;
            }

            LinearLayoutManager myLayoutManager = (LinearLayoutManager) layoutManager;

            int position1 = myLayoutManager.findFirstVisibleItemPosition();
            int position2 = myLayoutManager.findLastVisibleItemPosition();

            int currentPosition = layoutManager.getPosition(currentView);

            if (velocityX > 400) {
                currentPosition = position2;
            } else if (velocityX < 400) {
                currentPosition = position1;
            }

            if (currentPosition == RecyclerView.NO_POSITION) {
                return RecyclerView.NO_POSITION;
            }

            return currentPosition;
        }
    }


}
