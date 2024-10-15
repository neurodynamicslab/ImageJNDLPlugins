/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

//package ndl.ndllib;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackProcessor;
import ij.process.StackStatistics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;

import inra.ijpb.algo.DefaultAlgoListener;
import inra.ijpb.binary.distmap.ChamferMask3D;
import inra.ijpb.binary.distmap.ChamferMasks3D;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3D;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3DFloat;
import inra.ijpb.color.ColorMaps;
import inra.ijpb.data.image.ImageUtils;
import inra.ijpb.data.image.Images3D;
import java.awt.Point;
/**
 * A plugin for measuring the inter spine distances from a geodesic map. Each of the dendrite is
 * is identified by a unique number. The spine image is used to generate measurement mask. This is mask is later used on the 
 * geodesic distance image to obtain the spines with their distance measured from internal reference point in each of the dendrite. 
 */
public class Spine_Geodesic implements PlugInFilter {
	protected ImagePlus image;

	// image property members
	private int width;
	private int height;

	// plugin parameters
	public double value;
	public String name;
        
        ArrayList<ImagePlus> dendriteSels, spineSels;
        ArrayList<String> errFile;
        ArrayList<Roi> selectionRois;
        ArrayList <ArrayList<ImagePlus>> brainImage = new ArrayList(); ///*this has 2 elements only one for dendrite sel image and other for coresponding spine selection*/
        ArrayList/*<JTable or JList or ArrayList<String>>*/ results = new ArrayList(); // for storing the measurements from the images
        
        MultiFileDialog FD = new MultiFileDialog(null,true);

        
        

	@Override
	public int setup(String arg, ImagePlus imp) {
//		if (arg.equals("about")) {
//			showAbout();
//			return DONE;
//		}
//
//		image = imp;
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB | DOES_STACKS;
	}

	@Override
	public void run(ImageProcessor ip) {
                
                //Open a multi file doalog and get a list of files to work on.
                String[] dendfNames,spinefNames;
                boolean errStatus;
               
                FD.setTitle("Select the files with dendritic selections");
                FD.setVisible(true);
                
                errStatus =   ! (FD.getResult()== 2) ; //selection is made and has atleast one file
                
                if (errStatus){
                    System.out.printf("Please select a file : %d", FD.getResult());
                    return;
                }else
                    dendfNames = FD.getSelectionArray();
                
                FD.setTitle("Select the files with spine selections (in the same order)");
                FD.setVisible(true);
                
                errStatus = ! ( FD.getResult() == 2 );
                
                if (! errStatus){
                   
                    spinefNames = FD.getSelectionArray();
                    
                    errStatus =  !( spinefNames.length == dendfNames.length);
                    
                    if(errStatus)
                        return;
                    
                    //TODO : show the paired fnames in a dual window list and allow the user to change the pairing
                    int fCount;
                    ImagePlus tmp;
                    dendriteSels = new ArrayList();
                    errFile = new ArrayList();
                    
                    for (String fname : dendfNames){
                        //TODO: Add check for 
                        tmp = new ImagePlus(fname);
                        if(tmp != null)
                            dendriteSels.add(tmp);
                        else{
                            errFile.add(fname);
                            System.out.println(fname);
                        }
                    }
                    convert2geodesic(dendriteSels,dendfNames);
                    
                    for (fCount = 0;fCount < dendriteSels.size(); fCount++) {
                        ImagePlus imp = (ImagePlus) dendriteSels.get(fCount);
                        String destname = dendfNames[fCount];
                        if (! errFile.contains(destname)){
//                            destname = destname.split("\\.")[0];
//                            destname += "_geo.tiff";
//                            IJ.saveAsTiff(imp, destname);
                            
                            ImagePlus spineimp = new ImagePlus(spinefNames[fCount]);
                            ArrayList tmpArray = new ArrayList();
                            tmpArray.add(imp);
                            tmpArray.add(spineimp);
                            brainImage.add(tmpArray);
                        }
                        fCount++;
                    }
                    
                    makeMeasurements(brainImage, results);
                    
                }
               
                
                
                
                
		// get width and height
		//width = ip.getWidth();
		//height = ip.getHeight();

		/*if (showDialog()) {
			process(ip);
			image.updateAndDraw();
		}*/
                
                
	}

	/*private boolean showDialog() {
		GenericDialog gd = new GenericDialog("Process pixels");

		// default value is 0.00, 2 digits right of the decimal point
		gd.addNumericField("value", 0.00, 2);
		gd.addStringField("name", "John");

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		value = gd.getNextNumber();
		name = gd.getNextString();

		return true;
	}*/

	/**
	 * Process an image.
	 * <p>
	 * Please provide this method even if {@link ij.plugin.filter.PlugInFilter} does require it;
	 * the method {@link ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)} can only
	 * handle 2-dimensional data.
	 * </p>
	 * <p>
	 * If your plugin does not change the pixels in-place, make this method return the results and
	 * change the {@link #setup(java.lang.String, ij.ImagePlus)} method to return also the
	 * <i>DOES_NOTHING</i> flag.
	 * </p>
	 *
	 * @param image the image (possible multi-dimensional)
	 */
	/*public void process(ImagePlus image) {
		// slice numbers start with 1 for historical reasons
		for (int i = 1; i <= image.getStackSize(); i++)
			process(image.getStack().getProcessor(i));
	}

	// Select processing method depending on image type
	public void process(ImageProcessor ip) {
		int type = image.getType();
		if (type == ImagePlus.GRAY8)
			process( (byte[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY16)
			process( (short[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY32)
			process( (float[]) ip.getPixels() );
		else if (type == ImagePlus.COLOR_RGB)
			process( (int[]) ip.getPixels() );
		else {
			throw new RuntimeException("not supported");
		}
	}

	// processing of GRAY8 images
	public void process(byte[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (byte)value;
			}
		}
	}

	// processing of GRAY16 images
	public void process(short[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (short)value;
			}
		}
	}

	// processing of GRAY32 images
	public void process(float[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (float)value;
			}
		}
	}

	// processing of COLOR_RGB images
	public void process(int[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (int)value;
			}
		}
	}

	public void showAbout() {
		IJ.showMessage("ProcessPixels",
			"a template for processing each pixel of an image"
		);
	}

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads
	 * an image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = Spine_Geodesic.class;
		java.net.URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
		java.io.File file = new java.io.File(url.toURI());
		System.setProperty("plugins.dir", file.getAbsolutePath());
                System.out.println("Plugin dir is set to:" + file.getAbsolutePath()+"\n");
		// start ImageJ
		new ImageJ();

//		// open the Clown sample
//		ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
//		image.show();

		// run the plugin
		//IJ.runPlugIn(clazz.getName(), "");
	}
        private void convert2geodesic(ImagePlus img,String fname){
            
            ImagePlus marker;
            StackStatistics stat = new StackStatistics(img);
            int lowerInt = stat.min == 0 ? 1 :(int) Math.floor(stat.min) ;              //ideally this can be set to 1 as the enumeration of objects are integer
            int highInt  = (int)Math.ceil(stat.max);
            
            ImageStack stk = img.getStack();
            int stkSize = stk.getSize();
            int startSlice = -1;
            int endSlice = -1;
            ImageStack inStk = new ImageStack();
            
            for (int number = lowerInt ; number <= highInt ; number++){
            
                for(int slice = 1 ; slice <= stkSize ; slice++){
                    
                }
            }
        }

    private void convert2geodesic(ArrayList dendriteSels, String[] fname) {
       
                ThresholdToSelection roiCreator = new ThresholdToSelection();
                ChamferMask3D chamferMask;
                chamferMask =  ChamferMask3D.SVENSSON_3_4_5_7;
                for (Object o : dendriteSels){                              //run thru the images
                    ImagePlus tmp = (ImagePlus)o;
                    ImagePlus marker = tmp.createImagePlus();
                    
                    
//                    marker.setProcessor(tmp.getProcessor().duplicate().convertToByteProcessor());
//                    
//                    ByteProcessor markerProcessor = (ByteProcessor)marker.getProcessor();
//                    markerProcessor.set(0);
                    //get the minimum and maximum intensity to identify the number of objects
                    //select out individual intensities from min to max
                    //identify the start pixel (left top ?) as marker for each object
                    //generate marker image (invert the mask and set the marker pixel as 1)
                    //run the geodesic
                    
                    StackStatistics stat = new StackStatistics(tmp);
                   
                    double lowerBnd = (stat.min == 0 ) ? 1 : stat.min;
                    double upperBnd = stat.max;
                    //double dendriteNo = upperBnd - lowerBnd;
                    int stackSize = tmp.getStackSize();
                   
                    ImageStack stack = tmp.getStack();
                    ImageStack resStack = stack.duplicate();
                    ImageStack markerStk = stack.duplicate();
                    marker.setStack("Marker", markerStk);
                    for(int s = 1 ; s <= markerStk.getSize(); s ++){
                        markerStk.getProcessor(s).convertToByteProcessor();
                        markerStk.getProcessor(s).set(0);
                    }
                    if( stack.getSize() != markerStk.getSize()){
                    
                        System.out.println("Internal Error ! Raw stack is not mathcing the marker" + stack.getSize()+" " +markerStk.getSize());
                        return;
                    }
                    Rectangle rect = new Rectangle(0,0,0,0);//,closeRect = new Rectangle(0,0,0,0);
                    Point closePoint = new Point(0,0);
                    float curSqDist, minSqDist, maxSqDist = tmp.getHeight()*tmp.getHeight() + tmp.getWidth()*tmp.getWidth();
                    int minSqinSlice = 1;
                    ImageProcessor tempPro;
                    ShapeRoi overalRoi = null;
                    
                    for(long dendCount = (long)lowerBnd ; dendCount <= upperBnd ; dendCount++){
                      
//                        ImageProcessor ip = tmp.getProcessor().duplicate();
//                      //tmp.show();
//                      //this.wait(100);
//                        ip.setThreshold(dendCount, dendCount);
                        minSqDist = maxSqDist;
//                      ImageStatistics s = ip.getStatistics();
//                      System.out.println(""+s.area+ " M = " +s.mean);
//                      //IJ.run("Create Selection");
//                      ByteProcessor mask = ip.createMask();
                        boolean roiSet = false;
                        for(int sliceNo = 1 ; sliceNo <= stackSize ; sliceNo++){
                            tempPro = stack.getProcessor(sliceNo).duplicate();
                            tempPro.setThreshold(dendCount, dendCount);
                            
                            Roi roi =  roiCreator.convert(tempPro);
                            
                            if(roi != null){
                                //rect = roi.getBounds();
                                //find a start point by finding the minimum y and minimum x. 
                                double [] des = roi.getFeretValues();
                                int x = (int)des[8];
                                int y = (int)des[9];
                                curSqDist = x*x + y*y ;
                                if(curSqDist < minSqDist){
                                    minSqDist = curSqDist;
                                    closePoint = new Point(x,y);
                                    minSqinSlice = sliceNo;
                                }
                                if(overalRoi == null)
                                    overalRoi = new ShapeRoi(roi) ;
                                else 
                                    overalRoi.or(new ShapeRoi (roi));
                                roiSet = true;
                            }
                            //System.out.println(""+closeRect.x+ " M = " +closeRect.y);
                        }
                        if(roiSet){
                            System.out.println(""+closePoint.x+ " M = " +closePoint.y + "in Slice# "+ minSqinSlice + "ObjID " + dendCount);
                            marker.getStack().getProcessor(minSqinSlice).set(closePoint.x, closePoint.y, 255);
                      //estimate the start pixel and set that pixelvalue to 1 in marker image
                        }else{
                            System.out.println("None" + "ObjID " + dendCount);
                        }
                   }
                  // marker.show();
                   GeodesicDistanceTransform3D algo = new GeodesicDistanceTransform3DFloat(chamferMask, true);
                   DefaultAlgoListener.monitor(algo);
    	

                    // Compute distance on specified images
                    
                    ImageStack result = algo.geodesicDistanceMap(marker.getImageStack(), tmp.getImageStack()); 
                    tmp.setStack(result);
                    IJ.saveAsTiff(tmp,fname[0]+"_geo" );
                    //IJ.saveAsTiff(marker,fname[0]+"mark");
                       //(marker.getImageStack(),tmp.getImageStack());
                        //use the marker stack and tmp to get the geodesic
                        //savegeodesic
                }
    }

    private void makeMeasurements(ArrayList<ArrayList<ImagePlus>> brainImage, ArrayList results1) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
