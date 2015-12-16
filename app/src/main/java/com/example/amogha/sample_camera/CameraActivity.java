package com.example.amogha.sample_camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressWarnings("deprecation")
public class CameraActivity extends AppCompatActivity
		implements
			SensorEventListener {

	private static final String TAG = "CameraTest";
	CameraPreview mPreview;
	Camera mCamera;
	Activity mCameraActivity;
	Context mContext;
	private SensorManager sensorManager = null;
	private int orientation;
	private ExifInterface exif;
	private int degrees = -1;
	private Button mTakePictureButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		mContext = this;
		mCameraActivity = this;
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		mPreview = new CameraPreview(this,
				(SurfaceView) findViewById(R.id.cameraSurfaceView));
		mPreview.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		((RelativeLayout) findViewById(R.id.cameraLayout)).addView(mPreview);
		mPreview.setKeepScreenOn(true);

		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mTakePictureButton = (Button) findViewById(R.id.takePictureBtn);
		mTakePictureButton.setOnClickListener(takePictureClickListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		int numCams = Camera.getNumberOfCameras();
		if (numCams > 0) {
			try {
				if (mCamera == null)
					mCamera = Camera.open(0);
				mCamera.startPreview();
				Log.d(TAG, "Camera preview started onResume");
				mPreview.setCamera(mCamera);
			} catch (RuntimeException ex) {
				Toast.makeText(mContext, "Camera not found", Toast.LENGTH_LONG)
						.show();
			}
		}
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mPreview.setCamera(null);
			mCamera.release();
			mCamera = null;
		}
		super.onDestroy();
	}

	private void resetCam() {
		mCamera.startPreview();
		Log.d(TAG, "Camera preview started onResume");
		mPreview.setCamera(mCamera);
	}

	private void refreshGallery(File file) {
		Intent mediaScanIntent = new Intent(
				Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		mediaScanIntent.setData(Uri.fromFile(file));
		sendBroadcast(mediaScanIntent);
	}

	private View.OnClickListener takePictureClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
		}
	};

	Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
		public void onShutter() {
			// Log.d(TAG, "onShutter'd");
		}
	};

	Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			// Log.d(TAG, "onPictureTaken - raw");
		}
	};

	Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			new SaveImageTask().execute(data);
			resetCam();
			Log.d(TAG, "onPictureTaken - jpeg");
		}
	};

	@Override
	public void onSensorChanged(SensorEvent event) {
		synchronized (this) {
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
				if (event.values[0] < 4 && event.values[0] > -4) {
					if (event.values[1] > 0
							&& orientation != ExifInterface.ORIENTATION_ROTATE_90) {
						orientation = ExifInterface.ORIENTATION_ROTATE_90;
						degrees = 270;
					} else if (event.values[1] < 0
							&& orientation != ExifInterface.ORIENTATION_ROTATE_270) {
						orientation = ExifInterface.ORIENTATION_ROTATE_270;
						degrees = 90;
					}
				} else if (event.values[1] < 4 && event.values[1] > -4) {
					if (event.values[0] > 0
							&& orientation != ExifInterface.ORIENTATION_NORMAL) {
						orientation = ExifInterface.ORIENTATION_NORMAL;
						degrees = 0;
					} else if (event.values[0] < 0
							&& orientation != ExifInterface.ORIENTATION_ROTATE_180) {
						orientation = ExifInterface.ORIENTATION_ROTATE_180;
						degrees = 180;
					}
				}
			}

		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

		@Override
		protected Void doInBackground(byte[]... data) {
			FileOutputStream outStream = null;

			File sdCard = Environment.getExternalStorageDirectory();
			String filePathName = sdCard.getAbsolutePath() + "/CamTest/";
			File dir = new File(filePathName);
			String fileName = String.format("%d.jpg",
					System.currentTimeMillis());
			try {
				dir.mkdirs();

				File outFile = new File(dir, fileName);

				outStream = new FileOutputStream(outFile);
				outStream.write(data[0]);
				outStream.flush();
				outStream.close();

				Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length
						+ " to " + outFile.getAbsolutePath());

				refreshGallery(outFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			}

			try {
				exif = new ExifInterface(filePathName + fileName);
				exif.setAttribute(ExifInterface.TAG_ORIENTATION, ""
						+ orientation);
				exif.saveAttributes();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
}
