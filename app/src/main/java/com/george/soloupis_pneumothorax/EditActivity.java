package com.george.soloupis_pneumothorax;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.isseiaoki.simplecropview.CropImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.Vector;

public class EditActivity extends AppCompatActivity {

    public static final int ACTION_TAKE_PHOTO_B = 1;
    public static Bitmap bitmap;
    public static int chose;
    private static Context context;
    private Vector<Bitmap> bitmapVec = new Vector();
    private int deviceWidth;
    private String mCurrentPhotoPath = " ";
    private ImageView mImageView;
    private SharedPreferences prfs;
    Point size = new Point();
    private RelativeLayout dyn_layout_img;
    private CropImageView mCropView;
    private ImageButton btnKeyStone;
    private ImageButton btnDone;
    private ImageButton btnRotateRight;
    private ImageButton btnShow;
    private static final String INTENT_CHOOSER_MESSAGE = "Share photo!";
    private int numberForImageEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        /*getSupportActionBar().setTitle(R.string.crop_image);*/
        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();

        // Enable the Up button
        assert ab != null;
        ab.setDisplayHomeAsUpEnabled(true);

        setTitle(getResources().getString(R.string.shareImage));

        mCropView = findViewById(R.id.cropImageView);
        btnDone = findViewById(R.id.btnDone);
        btnRotateRight = findViewById(R.id.btnRotateRight);
        mImageView = findViewById(R.id.imgview);

        context = this;
        this.deviceWidth = this.size.x;
        /////////////

        dyn_layout_img = findViewById(R.id.dyn_layout_img);
        this.prfs = getSharedPreferences("prefs", 0);

        Intent localIntent = getIntent();
        if (localIntent.hasExtra("CameraPath")) {
            this.mCurrentPhotoPath = localIntent.getExtras().getString("CameraPath");
            bitmap = BitmapFactory.decodeFile(this.mCurrentPhotoPath);

            Drawable d = new BitmapDrawable(getResources(), bitmap);
            mCropView.setImageDrawable(d);
            mCropView.setInitialFrameScale(1.00f);
        }
        /*chose = localIntent.getIntExtra("Choose", 1);
        Uri localUri;
        if (chose == 4) {
            localUri = localIntent.getParcelableExtra("imageUri");
            try {
                bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(localUri));

                Drawable d = new BitmapDrawable(getResources(), bitmap);
                mCropView.setImageDrawable(d);
                mCropView.setInitialFrameScale(1.00f);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (chose == ACTION_TAKE_PHOTO_B) {
            this.mCurrentPhotoPath = localIntent.getExtras().getString("CameraPath");
            bitmap = BitmapFactory.decodeFile(this.mCurrentPhotoPath);

            Drawable d = new BitmapDrawable(getResources(), bitmap);
            mCropView.setImageDrawable(d);
            mCropView.setInitialFrameScale(1.00f);
        }*/
    }

    //use it for TransactionTooLargeException
    @Override
    protected void onSaveInstanceState(Bundle oldInstanceState) {
        super.onSaveInstanceState(oldInstanceState);
        oldInstanceState.clear();
    }

    public void btnRotateRightclick(View view) {
        /////////////rotate right + miliseconds
        mCropView.rotateImage(CropImageView.RotateDegrees.ROTATE_90D, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mImageView.setImageBitmap(null);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Random random = new Random();
        numberForImageEdit = random.nextInt(10000) + 1;
    }

    public void btnDoneclick(View view) {
        mImageView.setImageBitmap(mCropView.getCroppedBitmap());
        mCropView.setImageBitmap(mCropView.getCroppedBitmap());

        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        mImageView.setColorFilter(filter);

        Bitmap image = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
        /*Bitmap grey = toGrayscale(image);*/

        saveImageFile(image);

        /*Uri uri = Uri.fromFile(new File(Environment.getExternalStorageDirectory().toString() + "/" + "Wheels/Wheels.jpg"));*/
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", new File(Environment.getExternalStorageDirectory().toString() + "/Wheels/"+numberForImageEdit+".jpg"));

        composeEmail(uri);

        /*Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                // Actions to do after 1 second
                //Compose mail

            }
        }, 1000);*/

        /////////////////////////
        /*finish();*/
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    public String saveImageFile(Bitmap bitmap) {
        FileOutputStream out = null;
        String filename = getFilename();
        try {
            out = new FileOutputStream(filename);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return filename;
    }

    public String getFilename() {
        File file = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Wheels");
        if (!file.exists()) {
            file.mkdirs();
        }
        String uriSting = (file.getAbsolutePath() + "/" + numberForImageEdit + ".jpg");
        return uriSting;
    }

    public void composeEmail(Uri attachment) {
        String text = getString(R.string.mainShareApp);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, attachment);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, INTENT_CHOOSER_MESSAGE));
        }
    }

}
