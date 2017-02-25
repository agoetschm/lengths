package ch.goetschy.android.lengths;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Area;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String TAG = "CamTestActivity";

    private static final int AVERAGING_DELAY = 300; // in ms

    private Preview preview;
    private Camera camera;

    // private TextView mTv;

    // SENSOR
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;

    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;

    private float[] mR = new float[16];
    private float[] mOrientation = new float[3];
    private float[] mLastOrientation = new float[3];

    // sensor averaging
    private long mLastTime = 0;
    private int mNbrAccValues;
    private float[] mAccOrientation = new float[3];

    // saved values
    private static final int FIRST_POINT = 0;
    private static final int SECOND_POINT = 1;
    private static final int DONE = 2;
    private int mPhase = FIRST_POINT;
    private float[] mFirstPointAngle = new float[3];

    // engine
    Engine mEngine = new Engine();

    // display
    private ProgressBar mRollBar;
    private ProgressBar mPitchBar;
    private ImageView mAzimuthBar;

    private Button mABut;
    private Button mBBut;
    private Button mABBut;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // get views
        mRollBar = (ProgressBar) findViewById(R.id.rollBar);
        mPitchBar = (ProgressBar) findViewById(R.id.pitchBar);
        mAzimuthBar = (ImageView) findViewById(R.id.azimutBar);

        mABut = (Button) findViewById(R.id.pointA);
        mBBut = (Button) findViewById(R.id.pointB);
        mABBut = (Button) findViewById(R.id.distAB);

        preview = new Preview(this,
                (SurfaceView) findViewById(R.id.main_surfaceview));
        preview.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        ((FrameLayout) findViewById(R.id.layout)).addView(preview);
        preview.setKeepScreenOn(true);

        // test TODO remove
        // mTv = (TextView) this.findViewById(R.id.main_text);

        // sensors
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // LISTENERS ------------
        mABut.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPhase == FIRST_POINT) {
                    savePoint();
                    mBBut.setEnabled(true);
                } else {
                    mPhase = FIRST_POINT;
                    mABut.setText(R.string.butADefault);
                    mBBut.setText(R.string.butBDefault);
                    mBBut.setEnabled(false);
                    mABBut.setText(R.string.butABDefault);
                    mABBut.setEnabled(false);
                }
            }
        });

        mBBut.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPhase == SECOND_POINT) {
                    savePoint();
                    mABBut.setEnabled(true);
                } else {
                    mPhase = SECOND_POINT;
                    mBBut.setText(R.string.butBDefault);
                    mABBut.setText(R.string.butABDefault);
                    mABBut.setEnabled(false);
                }
            }
        });

        mABBut.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Lengths",
                        "" + mEngine.getDistAtoB() + "m");
                clipboard.setPrimaryClip(clip);
                makeToast("Distance copied to clipboard");
            }
        });

        preview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (camera != null) {
                    camera.cancelAutoFocus();
                    Parameters parameters = camera.getParameters();
                    if (parameters.getFocusMode() != Camera.Parameters.FOCUS_MODE_AUTO) {
                        parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
                    }
                    ArrayList<Area> focusAreas = new ArrayList<Camera.Area>(1);
                    focusAreas.add(new Area(new Rect(-1000, -1000, 1000, 0), 750));
                    parameters.setFocusAreas(focusAreas);

                    try {
                        camera.cancelAutoFocus();
                        camera.setParameters(parameters);
                        camera.startPreview();
                        camera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success,
                                    Camera camera) {
                                if (camera.getParameters().getFocusMode() != Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) {
                                    Parameters parameters = camera
                                            .getParameters();
                                    parameters
                                            .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                                    parameters.setFocusAreas(null);
                                    camera.setParameters(parameters);
                                    camera.startPreview();
                                }
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }
        });

        // INTRO DIALOG
        showDialog();
    }

    private void showDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.setTitle("Hello !");
        dialog.setContentView(R.layout.intro_dialog);
        ((EditText) dialog.findViewById(R.id.height_edit)).setText(String
                .valueOf(mEngine.getHeight()));
        ((Button) dialog.findViewById(R.id.button_close_dialog))
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mEngine.setHeight(Double.parseDouble(((EditText) dialog
                                .findViewById(R.id.height_edit)).getText()
                                .toString()));
                        dialog.dismiss();
                    }
                });
        dialog.show();
    }

    protected void savePoint() {
        if (mPhase == FIRST_POINT) {
            for (int i = 0; i < 3; i++)
                mFirstPointAngle[i] = mLastOrientation[i];
            // calculate dist
            mEngine.setAlpha(-mLastOrientation[1]);
            // next phase
            mPhase = SECOND_POINT;
        } else {
            mEngine.setBeta(-mLastOrientation[1]);
            mEngine.setGamma(Math
                    .abs(mLastOrientation[0] - mFirstPointAngle[0]));
            mPhase = DONE;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int numCams = Camera.getNumberOfCameras();
        if (numCams > 0) {
            try {
                camera = Camera.open(0);
                camera.setDisplayOrientation(90); // portrait
                camera.startPreview();
                preview.setCamera(camera);
            } catch (RuntimeException ex) {
                Toast.makeText(this, "cam not found", Toast.LENGTH_LONG).show();
            }
        }

        mLastAccelerometerSet = false;
        mLastMagnetometerSet = false;
        mSensorManager.registerListener(this, mAccelerometer,
                SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mMagnetometer,
                SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        if (camera != null) {
            camera.stopPreview();
            preview.setCamera(null);
            camera.release();
            camera = null;
        }
        // unregister the sensor listener
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    private void resetCam() {
        camera.startPreview();
        preview.setCamera(camera);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.help:
            showDialog();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // if sensor is unreliable, return void
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return;
        }

        if (event.sensor == mAccelerometer) {
            System.arraycopy(event.values, 0, mLastAccelerometer, 0,
                    event.values.length);
            mLastAccelerometerSet = true;
        } else if (event.sensor == mMagnetometer) {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0,
                    event.values.length);
            mLastMagnetometerSet = true;
        }
        if (mLastAccelerometerSet && mLastMagnetometerSet) {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer,
                    mLastMagnetometer);
            SensorManager.getOrientation(mR, mOrientation);

            long currentTime = System.currentTimeMillis();
            if (currentTime - mLastTime >= AVERAGING_DELAY) {
                mLastTime = currentTime;

                // divide
                for (int i = 0; i < 3; i++) {
                    mOrientation[i] = mLastOrientation[i] = mAccOrientation[i]
                            / mNbrAccValues;
                    mAccOrientation[i] = 0; // reinit
                }
                mNbrAccValues = 0; // reinit nbr acc values

                Log.i("OrientationTestActivity",
                        String.format("Orientation: %f, %f, %f",
                                mOrientation[0], mOrientation[1],
                                mOrientation[2])
                                + " : " + System.currentTimeMillis());

                // String addTxt = "";
                // if (mPhase >= SECOND_POINT)
                // addTxt += "\nsaved : " + mFirstPointAngle[0] + "/"
                // + mFirstPointAngle[1] + "/" + mFirstPointAngle[2]
                // + "\ndistance to A : " + mEngine.getDistA();
                // if (mPhase == DONE)
                // addTxt += "\ndistance to B : " + mEngine.getDistB()
                // + "\ndistance from A to B : "
                // + mEngine.getDistAtoB();
                // output the Roll, Pitch and Yawn values
                // mTv.setText("azimuth, rotation around the Z axis :"
                // + mOrientation[0] + "\n"
                // + "pitch, rotation around the X axis :"
                // + mOrientation[1] + "\n"
                // + "roll, rotation around the Y axis :"
                // + mOrientation[2] + addTxt);

                // display on buttons
                displayButs();

                // display orientation bars
                mRollBar.setProgress((int) (-mOrientation[2] * 50.0 / Math.PI + 50));
                mPitchBar
                        .setProgress((int) (-mOrientation[1] * 50.0 / Math.PI + 50));
                mAzimuthBar
                        .setRotation((float) (-mOrientation[0] / Math.PI * 180));

            } else {
                // accumulate
                for (int i = 0; i < 3; i++)
                    mAccOrientation[i] += mOrientation[i];
                this.mNbrAccValues += 1;
            }

        }

    }

    private void displayButs() {
        switch (mPhase) {
        case DONE:
            mBBut.setText("you -> B\n" + mEngine.getDistB() + "m");
            mABBut.setText("A -> B\n" + mEngine.getDistAtoB() + "m");
        case SECOND_POINT:
            mABut.setText("you -> A\n" + mEngine.getDistA() + "m");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub

    }

    public void makeToast(String txt) {
        Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
    }

}
