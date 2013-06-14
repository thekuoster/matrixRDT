package com.features.detector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AnalyzeTestStrip extends AsyncTask<Bitmap, Integer, Mat>{
	
	private final Activity context;
	
	private final int[] tests = {R.id.textTest1Result, R.id.textTest2Result, R.id.textTest3Result, R.id.textTest4Result, 
			R.id.textTest5Result, R.id.textTest6Result, R.id.textTest7Result, R.id.textTest8Result};
	
	// test results, everything else is an error
	private final int[] positive = {1,1,0,0};
	private final int[] negative = {1,0,0,0};
	
	// Look at http://docs.opencv.org/doc/tutorials/imgproc/imgtrans/canny_detector/canny_detector.html
	// for more information on choosing threshold values
	private double cannyThresh = 95;
	private double cannyRatio = .4;
	
	// Look at http://docs.opencv.org/doc/tutorials/imgproc/imgtrans/hough_lines/hough_lines.html
	// for more information on choosing threshold values
	private int houghThresh = 120;
	
	
	public AnalyzeTestStrip(Activity context){
		this.context = context;
	}
	
	private int imgWidth;
	private int imgHeight;
	
	private int grayThresh = 105;
	
	private int[][] markers = new int[4][8];
	
	@Override
	protected Mat doInBackground(Bitmap... params) {
		Mat imgInput = new Mat();
		Mat imgGray = new Mat();
		Utils.bitmapToMat(params[0], imgInput);
		
		// initiate the markers array with all negative
		for(int row = 0; row < 4; row++){
			for(int col = 0; col < 8; col++){
				markers[row][col] = 0;
			}
		}
		
		imgWidth = imgInput.width();
		imgHeight = imgInput.height();
		
		// create a grayscale image
		Imgproc.cvtColor(imgInput, imgGray, Imgproc.COLOR_RGB2GRAY);
		
		Mat imgContours = new Mat();
		Imgproc.Canny(imgGray, imgContours, cannyRatio*cannyThresh, cannyThresh);
		
		// Output for the hough transform
		MatOfPoint2f lines = new MatOfPoint2f();
		Imgproc.HoughLines(imgContours, lines, 1, Math.PI/180, houghThresh);
		
		
		//Imgproc.cvtColor(imgInput, imgOutput, Imgproc.COLOR_GRAY2RGB);
		//imgInput.convertTo(imgOutput, Imgproc.COLOR_GRAY2BGR);
		
		Point[] lnsArray = lines.toArray();
		
		ArrayList<Line> lnList = new ArrayList<Line>();
		
		// iterate over found lines, converts from polar to cartesian coordinates
		for (int i = 0; i < lnsArray.length; i++){
			double theta = lnsArray[i].y;
			double rho = lnsArray[i].x;
			double a = Math.cos(theta), b = Math.sin(theta);
			double x0 = a*rho, y0 = b*rho;
			Point pt1 = new Point(Math.round(x0 + 1000*(-b)),
					Math.round(y0 + 1000*(a)));
			Point pt2 = new Point(Math.round(x0 - 1000*(-b)),
					Math.round(y0 - 1000*(a)));
			
			//there should be 4 lines
			lnList.add(new Line(pt1, pt2));
			
			
			// Draws result from hough transform
			//Core.line(imgOutput, pt1, pt2, new Scalar(255, 0, 0) , 1); (red lines)
		}
		
		/* Temporary statement, cancels task if the number of lines isn't equal to 4*/
		if(lnList.size() != 4){
			Log.w("Debug", "cancelled task");
			this.cancel(true);
		}
		
		if(!isCancelled()){	
			// get the intersection points of the lines
			Point[] corners = getIntersections(lnList);
			corners = sortCorners(corners);
			
			/*//draw the test area (blue lines)
			int numCorners = corners.length;
			for(int i = 0; i < numCorners; i++){
				Core.line(imgOutput, corners[i], corners[(i+1)%numCorners], new Scalar(0,0,255), 2);
			}*/
			
			//scanTestArea(corners, imgOutput);
			
			List<Rect> pos = scanTestArea(corners, imgGray); // currently scans based on grayscale values
			
			Imgproc.cvtColor(imgInput, imgInput, Imgproc.COLOR_RGBA2RGB);
			
			// draw identified positive markers on the test strip
			for(Rect rect : pos){
				Point pt1 = new Point(rect.x, rect.y);
				Point pt2 = new Point(rect.x+rect.width, rect.y+rect.height);
				//Indicate identified markers with filled rectangles
				Core.rectangle(imgInput, pt1, pt2, new Scalar(0, 188, 00), -1);
			}
			
			// copies just the test area to a new image
			Mat testArea = imgInput.submat((int)corners[0].y, (int)corners[3].y, (int)corners[0].x, (int)corners[1].x);
			
			return testArea;
		}
		return null;
	}
	
	@Override
	protected void onPostExecute(Mat result) {
		if(result != null){
			super.onPostExecute(result);
			
			// change view to results page
			context.setContentView(R.layout.results_page);
			context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			
			// get columns of matrix
			for (int i = 0; i < tests.length; i++) {
				int[] res = new int[markers.length];
				for (int j = 0; j < markers.length; j++) {
					res[j] = markers[j][i];
				}
				TextView resultView = (TextView) context.findViewById(tests[i]);
				resultView.setText(getResult(res));
			}
			
			ImageView resImg = (ImageView) context.findViewById(R.id.imageResult);
			resImg.setBackgroundColor(Color.rgb(00,188,00));
			
			Bitmap outBmp = Bitmap.createBitmap(result.width(), result.height(), Bitmap.Config.ARGB_8888);
			Utils.matToBitmap(result, outBmp);
			
			resImg.setImageBitmap(outBmp);
			//imgView.setImageBitmap(outBmp);
		}
	}
	
	@Override
	protected void onCancelled() {
		super.onCancelled();
		
		CharSequence text = "Error: Image can't be analyzed. Please try again.";
		int duration = Toast.LENGTH_LONG;
		
		Toast error = Toast.makeText(context, text, duration);
		error.show();
		
		// go back to the beginning on error
		context.setContentView(R.layout.home_page);
		context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
	}
	
	/**
	 * Finds all the intersection points of a given set of lines. The relevant intersection points
	 * are those found to be within the bounds of the image being analyzed
	 * 
	 * @param lnList
	 * @return an array of the intersection points
	 */
	private Point[] getIntersections(ArrayList<Line> lnList){
		Set<Point> intersections = new HashSet<Point>();
		
		for(int i = 0; i < lnList.size(); i++){
			for(int j = i+1; j < lnList.size(); j++){
				Point is = getIntersection(lnList.get(i), lnList.get(j));
				if(is != null){
					// checks whether the intersection point is within the image
					if(is.x >= 0 && is.x < imgWidth && is.y >= 0 && is.y < imgHeight){
						intersections.add(is);
					}
				}
			}
		}
		
		Point[] result = new Point[intersections.size()];
		
		return intersections.toArray(result);
	}
	
	/**
	 * Gets the intersection point of two lines if it exists
	 * 
	 * @param l1
	 * @param l2
	 * @return
	 */
	private Point getIntersection(Line l1, Line l2){
		
		Point x = delta(l2.pt1, l1.pt1);
		Point d1 = delta(l1.pt2, l1.pt1);
		Point d2 = delta(l2.pt2, l2.pt1);
		
		double cross = d1.x*d2.y - d1.y*d2.x;
		if(Math.abs(cross) < 1E-8){
			return null;
		}
		
		double t1 = (x.x * d2.y - x.y * d2.x) / cross;
		Point is = new Point(l1.pt1.x + d1.x * t1, l1.pt1.y + d1.y * t1);
		
		return is;
		
	}
		
	/**
	 * Performs the pixel analysis of the the test area of the image
	 * 
	 * @param corners the list of corners starting from the top left and going clockwise of the rectangle enclosing the test area
	 * @param output the 
	 * @return
	 */
	private List<Rect> scanTestArea(Point[] corners, Mat output){
		List <Rect> positives = new ArrayList<Rect>();
		
		double height = Math.round(lnLength(new Line(corners[0], corners[3])));
		double width = Math.round(lnLength(new Line(corners[0], corners[1])));
		
		/*Log.w("Debug", corners[0].toString());
		Log.w("Debug", corners[1].toString());
		Log.w("Debug", corners[2].toString());
		Log.w("Debug", corners[3].toString());*/
		
		double secWidth = width/19;
		double secHeight = height/5;
		
		int scanStart = (int) (corners[0].y+secHeight/2);
		int scanEnd = (int) (scanStart + 4*secHeight);
		
		Range first = new Range();
		Range second = new Range();
		Range third = new Range();
		Range fourth = new Range();
		
		Range[] sections = {first, second, third, fourth};
		
		// set the Y ranges that encompass each of the markers
		for(int i=0; i<=4; i++){
			Point pt1 = new Point(corners[0].x, corners[0].y + secHeight * (i + .5));
			//Point pt2 = new Point(pt1.x + width, pt1.y);
			
			if(i != 4){
				sections[i].start = (int)pt1.y;
			}
			
			if(i != 0){
				sections[i-1].end = (int)pt1.y;
			}
			//Core.line(output, pt1, pt2, new Scalar(0, 255, 0));
		}
		
		// keeps track of which test strip we are on
		int counter = 0;
		
		// controls scanning horizontally
		for(int i = 2; i <= 16; i+=2){
			//Point pt1 = new Point(corners[0].x + secWidth * (i+.5), corners[0].y);
			//Point pt2 = new Point(pt1.x, pt1.y+height);
			//Core.line(output, pt1, pt2, new Scalar(0, 255, 0));
			
			int x = (int) (corners[0].x+secWidth * (i+.5));
			
			// controls the vertical scan
			for (int j = scanStart; j <= scanEnd; j++){
				int start = -1;
				int end = -1;
				double[] vals = output.get(j, x);
			
				if(vals != null && vals[0] < grayThresh){
					
					// finds areas less than the gray threshold value
					start = j;
					while(j <= scanEnd && output.get(j, x)[0] < grayThresh){
						j++;
					}
					end = j;
					
					// gets the center of identified section section
					int middle = (start + end)/2;
					
					// small rectangle area inside the identified region
					Rect m = new Rect(x-2, middle-2, 3, 3); // 3 is arbitrary for this image. should probably be proportional to test are size
					
					// gets the average grayscale for the rectangular region
					if(getAverage(m, output) < grayThresh){
						int row = getSection(sections, middle);
						if(row >= 0){
							markers[row][counter]=1;
						}
						positives.add(m);
					}
					
				}
				
			}
			counter++;
		}
		return positives;
		
	}
	
	
	/**
	 * Sorts the corners in order from the topLeft going clockwise. Assumes there are only 4 corners to sort
	 * 
	 * @param corners list of corner points
	 * @return
	 */
	private Point[] sortCorners(Point[] corners){
		
		double maxLength = 0;
		Line maxSide = null;
		Point other = null;
		
		// Takes the first three intersections and finds the hypotenuse of the triangle to order the points
		for(int i = 0; i < 3; i++){
			for(int j = i+1; j < 3; j++){
				Line ln = new Line(corners[i], corners[j]);
				double length = lnLength(ln);
				if(length > maxLength){
					maxLength = length;
					maxSide = ln;
					other = corners[3-(i+j)];
				}
			}
		}
		
		// corners are ordered, but not necessarily with top left first and going clockwise
		Point[] halfSorted = {maxSide.pt1, other, maxSide.pt2, corners[3]};
		
		double minX1 = -1;
		double minX2 = -1;
		
		Point min1 = null;
		Point min2 = null;
		
		int index1 = -1;
		int index2 = -1;
		
		int numCorners = halfSorted.length;
		
		for(int i = 0; i < numCorners; i++){
			Point curr = halfSorted[i];
			if(minX1 < 0 || curr.x < minX1){
				if(minX2 < 0 || minX1 < minX2){
					minX2 = minX1;
					min2 = min1;
					index2 = index1;
				}
				minX1 = curr.x;
				min1 = curr;
				index1 = i;
			}
			else if(minX2 < 0 || curr.x < minX2){
				minX2 = curr.x;
				min2 = curr;
				index2 = i;
			}
		}
		
		int topLeft;
		int direction;
		
		// find which index is the top left point and get whether the points are ordered clockwise or counter-clockwise
		if(min1.y < min2.y){
			topLeft = index1;
			direction = getDirection(index1, index2, numCorners);
		}
		else{
			topLeft = index2;
			direction = getDirection(index2, index1, numCorners);
		}
		
		
		// resorts the corners
		Point[] sorted = new Point[numCorners];
		for(int i = 0; i < numCorners; i++){
			int index = (topLeft+(i*direction) + numCorners) % numCorners;
			sorted[i] = halfSorted[index];
		}
		
		return sorted;
	}
	
	/**
	 * Gets which direction the corners are currently ordered in
	 * 
	 * @param first index of the top left point
	 * @param second index of next point
	 * @param num 
	 * @return
	 */
	private int getDirection(int first, int second, int num){
		if(((first+1) % num) == second){
			return -1;
		}
		else{
			return 1;
		}
	}
	
	/**
	 * Finds the distance between the two points defining a line
	 * 
	 * @param ln
	 * @return
	 */
	private double lnLength (Line ln){
		Point p1 = ln.pt1;
		Point p2 = ln.pt2;
		return Math.sqrt(Math.pow(p1.x-p2.x,2) + Math.pow(p1.y-p2.y,2));
	}
	
	/**
	 * Obtains the delta between two points
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	private Point delta(Point p1, Point p2){
		return new Point(p1.x-p2.x, p1.y-p2.y);
	}
	
	private int getAverage(Rect rect, Mat gray){
		int sum = 0;
		for(int col = rect.x; col < rect.width + rect.x; col++){
			for(int row = rect.y; row< rect.height + rect.y; row++){
				double[] vals = gray.get(row, col);
				if(vals != null){
					sum += gray.get(row, col)[0];
				}
			}
		}
		return sum/(rect.width*rect.height);
	}
	
	/**
	 * Determines which marker within each test is being analyzed
	 * 
	 * @param sections
	 * @param center
	 * @return
	 */
	private int getSection(Range[] sections, int center){
		for (int i = 0; i < sections.length-1; i++) {
			Range sect = sections[i];
			if(center > sect.start && center < sect.end){
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * Returns the result of the test given the contents of each test column
	 * 
	 * @param test
	 * @return
	 */
	private int getResult(int[] test){
		if(Arrays.equals(test, positive)){
			return R.string.positive_result;
		}
		else if(Arrays.equals(test, negative)){
			return R.string.negative_result;
		}
		return R.string.error_result;
	}

	protected class Line{
		Point pt1;
		Point pt2;
		
		protected Line(Point p1, Point p2){
			pt1 = p1;
			pt2 = p2;
		}
	}
}
