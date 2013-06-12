package com.features.detector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class MatrixRDTActivity extends Activity {
	
	private static final String  TAG = "MatrixRDT";
	
	private static final int RESULT_LOAD_IMAGE = 1;
	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	
	private Button buttonFileChooser;
	private Button buttonCamera;
	
	private Activity context;
	
	private AnalyzeTestStrip analyzeTask;

	 private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    buttonFileChooser.setEnabled(true);
                    buttonCamera.setEnabled(true);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.home_page);
		
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		buttonCamera = (Button) findViewById(R.id.buttonCamera);
		buttonFileChooser = (Button) findViewById(R.id.buttonFileChooser);
		
		context = this;
	}
	

	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		analyzeTask = new AnalyzeTestStrip(context);
		
		if((requestCode == RESULT_LOAD_IMAGE || requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE)
				&& resultCode == RESULT_OK && data != null){
			
			setContentView(R.layout.progress_page);
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			
			Uri selectedImage = data.getData();
			String[] filePathColumn = { MediaStore.Images.Media.DATA };
			
			Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
			cursor.moveToFirst();
			
			int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
			String picturePath = cursor.getString(columnIndex);
			cursor.close();
			
			ImageView imageView = (ImageView) findViewById(R.id.imageView1);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(picturePath, options);
			
			Display display = getWindowManager().getDefaultDisplay();
			
			options.inSampleSize = calculateInSampleSize(options, display.getWidth(), display.getHeight());
			//options.inSampleSize = calculateInSampleSize(options, imageView.getWidth());
			
			options.inJustDecodeBounds = false;
			Bitmap bmp = BitmapFactory.decodeFile(picturePath, options);
			try{
				imageView.setImageBitmap(bmp);
			}
			catch(Exception e){
				CharSequence text = "Out of Memory: Image is too large try resizing image";
				int duration = Toast.LENGTH_LONG;
				
				Toast error = Toast.makeText(context, text, duration);
				error.show();
			}
			
			analyzeTask.execute(bmp);
		}
	}

	@Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.feature_detector, menu);
		return true;
	}
	
	/**
	 * Used to load a bitmap of varying size using less memory. 
	 * Taken from http://developer.android.com/training/displaying-bitmaps/index.html
	 * 
	 * @param options
	 * @param reqWidth	The max width of the space the bitmap is 
	 * @param reqHeight
	 * @return
	 */
	public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
	    // Raw height and width of image
	    final int height = options.outHeight;
	    final int width = options.outWidth;
	    int inSampleSize = 1;
	
	    if (height > reqHeight || width > reqWidth) {
	
	        // Calculate ratios of height and width to requested height and width
	        final int heightRatio = Math.round((float) height / (float) reqHeight);
	        final int widthRatio = Math.round((float) width / (float) reqWidth);
	
	        // Choose the smallest ratio as inSampleSize value, this will guarantee
	        // a final image with both dimensions larger than or equal to the
	        // requested height and width.
	        inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
	    }
	
	    return inSampleSize;
	}
	
	/**
	 * onClickListener function for the image chooser button. Opens the image chooser when
	 * the button is clicked
	 * 
	 * @param view
	 */
	public void imageChooser(View view){
		Intent i = new Intent(
				Intent.ACTION_PICK,
				android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		
		startActivityForResult(i, RESULT_LOAD_IMAGE);
	}
	
	
	/**
	 * onClickListener function for the camera button. Opens the stock camera app when the
	 * button is clicked
	 * 
	 * @param view
	 */
	public void openCamera(View view){
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		
		startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
	}

}
