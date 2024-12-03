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
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.StackStatistics;
import java.awt.Rectangle;
import java.util.ArrayList;

import inra.ijpb.algo.DefaultAlgoListener;
import inra.ijpb.binary.distmap.ChamferMask3D;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3D;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3DFloat;
import java.awt.Point;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
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
        
        ArrayList <Thread> monitor = new ArrayList();
        int threadCount;
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
                
                //FD.setTitle("Select the files with spine selections (in the same order)");
               // FD.setVisible(true);
                
                //errStatus = ! ( FD.getResult() == 2 );
                
                if (! errStatus){
                   
                    //spinefNames = FD.getSelectionArray();
                    
                   // errStatus =  !( spinefNames.length == dendfNames.length);
                    
                   // if(errStatus)
                       // return;
                    
                    //TODO : show the paired fnames in a dual window list and allow the user to change the pairing
                    int fCount = 0;
                    dendriteSels = new ArrayList();
                    errFile = new ArrayList();
                    String destPrefix = "_geo";
                    for (String fname : dendfNames){
                        //TODO: Add check for 
                        File tmpFile = new File(fname);//new ImagePlus(fname);
                        if(tmpFile.exists() && tmpFile.isFile()){
                            this.convert2geodesic(fname, destPrefix);
                            //fCount++;
                            //dendriteSels.add(tmp);
                            
                        }
                        else{
                            errFile.add(fname);
                            System.out.println("Error opening file : " + fname);
                        }
                    }
//                    convert2geodesic(dendriteSels,dendfNames);
                    
//                    for (fCount = 0;fCount < dendriteSels.size(); fCount++) {
//                        //ImagePlus imp = (ImagePlus) dendriteSels.get(fCount);
//                        String destname = dendfNames[fCount];
//                        if (! errFile.contains(destname)){
////                            destname = destname.split("\\.")[0];
////                            destname += "_geo.tiff";
////                            IJ.saveAsTiff(imp, destname);
//                            
////                            ImagePlus spineimp = new ImagePlus(spinefNames[fCount]);
////                            ArrayList tmpArray = new ArrayList();
////                            tmpArray.add(imp);
////                            tmpArray.add(spineimp);
////                            brainImage.add(tmpArray);
//                              this.convert2geodesic(dendfNames[fCount], destname);
//                        }
//                        fCount++;
//                    }
                    int activeCount = monitor.size();
                    while(activeCount > 0){
                        for (int count = 0 ; count < activeCount ; count ++){
                            if(!monitor.get(count).isAlive()){
                              activeCount--;
                                if(activeCount > 0) 
                                    System.out.println("Waiting for "+activeCount+ "threads to end out of " + monitor.size());
                                
                            }
                        }
                       try {
                            Thread.sleep(10);
                        } catch (InterruptedException ex) {
                            //Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    
                    System.out.println("All threads have ended");
                    
                    int remFiles = dendfNames.length - dendriteSels.size() + errFile.size();
                    
                    while(remFiles > 0){
                       try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            //Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        System.out.println("Waiting for writing " +remFiles + " remaining files");
                        remFiles = dendfNames.length - dendriteSels.size() + errFile.size();
                    }
                    
                    System.out.println("Out of "+dendfNames.length + " "+errFile.size() +" could not be processed");
                    
//                    makeMeasurements(brainImage, results);
                    
                }
               
                
                
                
                
		
                
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

// open the Clown sample
		ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
        /**
         * Given an image( img of the type ImagePlus) of objects with UID (as an integer value for all the connected pixels belonging to the object)
         * this method will run thru the object one by one and generate the geodesic image of object. The marker (reference point from which the distance is 
         * measured ) is generated from the object by identifying the nearest pixel defined as the pixel of the object with smallest distance from the top 
         * left corner of the image.
         * 
         * Algo:
         * 
         * Obtain the ImagePlus object and a file name for saving the result as arguments.
         * for (each objects in the image) 
         *  for ( all slices)
         *      estimate the closest distance from top left corner : done thru measuring Feret's descriptors. 
         *      save the slice and x, y co-ordinates of the nearest end of the Feret's long diameter.
         *      
         * @param img
         * @param fname 
         */
        private void convert2geodesic(String sourceImg,String destsuffix){
            ImagePlus img = new ImagePlus(sourceImg);
            if(img == null ){
                this.errFile.add(sourceImg);
                return;
            }
            ArrayList success, failure;
            success = this.dendriteSels;
            failure = this.errFile;
            SwingWorker worker = new SwingWorker(){
                @Override
                protected Object doInBackground() throws Exception {
                   
                            ThresholdToSelection roiCreator = new ThresholdToSelection();
                            ChamferMask3D chamferMask;
                            chamferMask =  ChamferMask3D.SVENSSON_3_4_5_7;

                            StackStatistics stat = new StackStatistics(img);
                            int lowerInt = stat.min == 0 ? 1 :(int) Math.floor(stat.min) ;              //ideally this can be set to 1 as the enumeration of objects are integer
                            int highInt  = (int)Math.ceil(stat.max);

                            ImageStack stk = img.getStack();
                            ImageStack resStk = new ImageStack();
                            ImageStack markStk; // = new ImageStack();
                            ImageStack maskStk,result ; //= new ImageStack();

                            int stkSize = stk.getSize();

                            for (int slice = 1 ; slice <= stkSize ; slice++)
                                resStk.addSlice(new FloatProcessor(stk.getWidth(),stk.getHeight()));


                            for (int number = lowerInt ; number <= highInt ; number++){

                                //ImagePlus marker;
                                ImageProcessor ip;
                                ByteProcessor slMask;
                                int startZ = -1,endZ = -1, depth, minSqinSlice = 1;
                                long curSqDist,minSqDist ;
                                //markStk = new ImageStack();
                                maskStk = new ImageStack();

                                ShapeRoi overAll = null;
                                boolean roiSet = false;
                                Point closePoint = new Point(0,0);
                                Rectangle bRect;
                                minSqDist = img.getWidth()*img.getWidth() + img.getHeight()*img.getHeight();

                                for(int slice = 1 ; slice <= stkSize ; slice++){

                                  ip = stk.getProcessor(slice);
                                  ip.setThreshold(number, number);
                                  Roi roi = roiCreator.convert(ip);
                                  slMask = ip.createMask();
                                  maskStk.addSlice(slMask);

                                  if(roi != null){

                                        startZ = (startZ == -1 )    ?   slice -1  :   startZ;
                                        //inStk.addSlice(ip.createMask());
                                        //rect = roi.getBounds();
                                        //find a start point by finding the minimum y and minimum x. 
                                        double [] des = roi.getFeretValues();
                                        //Point2D feretD = new Point2D(des[8],des[9]);
                                        int x = (int)des[8];
                                        int y = (int)des[9];
                                        if( ! roi.contains(x, y)){
                                            Point[] allPts = roi.getContainedPoints();
                                            //find nearest to x, y;
                                            int minIdx = 0, Idx = 0;
                                            double  minDist = 0, dist;
                                            for(Point Pt : allPts){
                                                dist = Pt.distance(des[8],des[9]);

                                                if(dist < minDist ){
                                                    minDist = dist;
                                                    minIdx = Idx;
                                                }  
                                                Idx++;
                                            }
                                            x = allPts[minIdx].x;
                                            y = allPts[minIdx].y;
                                        }

                                        curSqDist = x*x + y*y ;

                                        if(curSqDist < minSqDist){
                                            minSqDist = curSqDist;
                                            closePoint = new Point(x,y);
                                            minSqinSlice = slice;
                                        }

                                        if(overAll == null)
                                            overAll = new ShapeRoi(roi) ;
                                        else 
                                            overAll.or(new ShapeRoi (roi));

                                        roiSet = true;
                                        endZ = slice - 1;
                                  }
                                }
                                if(roiSet/*overAll != null && closePoint != null*/){
                                    bRect = overAll.getBounds();
                                    maskStk.setRoi(new Rectangle(bRect));
                                    depth = endZ - startZ +1;
                                    maskStk = maskStk.crop(bRect.x, bRect.y,startZ ,bRect.width, bRect.height,depth);
                                    markStk = maskStk.duplicate();
                                    for (int count = 1 ; count <= depth ; count++){
                                        markStk.getProcessor(count).convertToByteProcessor();
                                        markStk.getProcessor(count).set(0);
                                    }
                                    markStk.getProcessor(minSqinSlice-startZ).putPixelValue(closePoint.x-bRect.x,closePoint.y-bRect.y, 255);


                                    GeodesicDistanceTransform3D algo = new GeodesicDistanceTransform3DFloat(chamferMask, true);
                                    DefaultAlgoListener.monitor(algo);


                                    // Compute distance on specified images

                                    result = algo.geodesicDistanceMap(markStk, maskStk);
                                    int endslice = endZ+1;
                                    for (int slice = startZ +1,count = 1 ; slice <= endslice ; slice++, count++){
                                        resStk.getProcessor(slice).copyBits(result.getProcessor(count), bRect.x, bRect.y, Blitter.OR);
                                    }
                                }
                                //System.out.println("Finsihed upto "+ number + " objects with x , y, z at :" + closePoint.x + ","+ closePoint.y +"," +minSqinSlice);
                //                ImagePlus out = new ImagePlus();
                //                out.setStack(result);
                //                IJ.saveAsTiff(out, fname+"_geo_"+number);
                //                out.setStack(markStk);
                //                IJ.saveAsTiff(out, fname+"_mrk_"+number);
                            }
                            ImagePlus out = new ImagePlus();
                            out.setStack(resStk);
                            boolean fileStatus = IJ.saveAsTiff(out, sourceImg+ destsuffix);
                            if(fileStatus){
                                System.out.println("File :"+sourceImg+" processed");
                                success.add(sourceImg);
                            }
                            else{
                                System.out.println("Error writing File :"+sourceImg);
                                failure.add(sourceImg);
                            }
                            //img.setStack(resStk);
                //            out.setStack(markStk);
                //            IJ.saveAsTiff(out, fname+"_mrk");
                    return null;
                }
                
            };
            Thread tp = new Thread(worker,"gesodesic_"+threadCount);
            //worker.execute();
            tp.start();
            monitor.add(tp);
            threadCount++;
        }

//    private void convert2geodesic(ArrayList dendriteSels, String[] fname) {
//       
//                ThresholdToSelection roiCreator = new ThresholdToSelection();
//                ChamferMask3D chamferMask;
//                chamferMask =  ChamferMask3D.SVENSSON_3_4_5_7;
//                
//                for (Object o : dendriteSels){                              //run thru the images
//                    ImagePlus tmp = (ImagePlus)o;
//                    ImagePlus marker = tmp.createImagePlus();
//                    
//                    
////                    marker.setProcessor(tmp.getProcessor().duplicate().convertToByteProcessor());
////                    
////                    ByteProcessor markerProcessor = (ByteProcessor)marker.getProcessor();
////                    markerProcessor.set(0);
//                    //get the minimum and maximum intensity to identify the number of objects
//                    //select out individual intensities from min to max
//                    //identify the start pixel (left top ?) as marker for each object
//                    //generate marker image (invert the mask and set the marker pixel as 1)
//                    //run the geodesic
//                    
//                    StackStatistics stat = new StackStatistics(tmp);
//                   
//                    double lowerBnd = (stat.min == 0 ) ? 1 : stat.min;
//                    double upperBnd = stat.max;
//                    //double dendriteNo = upperBnd - lowerBnd;
//                    int stackSize = tmp.getStackSize();
//                   
//                    ImageStack stack = tmp.getStack();
//                    ImageStack resStack = stack.duplicate();
//                    ImageStack markerStk = stack.duplicate();
//                    marker.setStack("Marker", markerStk);
//                    for(int s = 1 ; s <= markerStk.getSize(); s ++){
//                        markerStk.getProcessor(s).convertToByteProcessor();
//                        markerStk.getProcessor(s).set(0);
//                    }
//                    if( stack.getSize() != markerStk.getSize()){
//                    
//                        System.out.println("Internal Error ! Raw stack is not mathcing the marker" + stack.getSize()+" " +markerStk.getSize());
//                        return;
//                    }
//                    Rectangle rect = new Rectangle(0,0,0,0);//,closeRect = new Rectangle(0,0,0,0);
//                    Point closePoint = new Point(0,0);
//                    float curSqDist, minSqDist, maxSqDist = tmp.getHeight()*tmp.getHeight() + tmp.getWidth()*tmp.getWidth();
//                    int minSqinSlice = 1;
//                    ImageProcessor tempPro;
//                    ShapeRoi overalRoi = null;
//                    
//                    for(long dendCount = (long)lowerBnd ; dendCount <= upperBnd ; dendCount++){
//                      
////                        ImageProcessor ip = tmp.getProcessor().duplicate();
////                      //tmp.show();
////                      //this.wait(100);
////                        ip.setThreshold(dendCount, dendCount);
//                        minSqDist = maxSqDist;
////                      ImageStatistics s = ip.getStatistics();
////                      System.out.println(""+s.area+ " M = " +s.mean);
////                      //IJ.run("Create Selection");
////                      ByteProcessor mask = ip.createMask();
//                        boolean roiSet = false;
//                        for(int sliceNo = 1 ; sliceNo <= stackSize ; sliceNo++){
//                            tempPro = stack.getProcessor(sliceNo).duplicate();
//                            tempPro.setThreshold(dendCount, dendCount);
//                            
//                            Roi roi =  roiCreator.convert(tempPro);
//                            
//                            if(roi != null){
//                                //rect = roi.getBounds();
//                                //find a start point by finding the minimum y and minimum x. 
//                                double [] des = roi.getFeretValues();
//                                int x = (int)des[8];
//                                int y = (int)des[9];
//                                curSqDist = x*x + y*y ;
//                                if(curSqDist < minSqDist){
//                                    minSqDist = curSqDist;
//                                    closePoint = new Point(x,y);
//                                    minSqinSlice = sliceNo;
//                                }
//                                if(overalRoi == null)
//                                    overalRoi = new ShapeRoi(roi) ;
//                                else 
//                                    overalRoi.or(new ShapeRoi (roi));
//                                roiSet = true;
//                            }
//                            //System.out.println(""+closeRect.x+ " M = " +closeRect.y);
//                        }
//                        if(roiSet){
//                            System.out.println(""+closePoint.x+ " M = " +closePoint.y + "in Slice# "+ minSqinSlice + "ObjID " + dendCount);
//                            marker.getStack().getProcessor(minSqinSlice).set(closePoint.x, closePoint.y, 255);
//                      //estimate the start pixel and set that pixelvalue to 1 in marker image
//                        }else{
//                            System.out.println("None" + "ObjID " + dendCount);
//                        }
//                   }
//                  // marker.show();
//                   GeodesicDistanceTransform3D algo = new GeodesicDistanceTransform3DFloat(chamferMask, true);
//                   DefaultAlgoListener.monitor(algo);
//    	
//
//                    // Compute distance on specified images
//                    
//                    ImageStack result = algo.geodesicDistanceMap(marker.getImageStack(), tmp.getImageStack()); 
//                    tmp.setStack(result);
//                    IJ.saveAsTiff(tmp,fname[0]+"_geo" );
//                    //IJ.saveAsTiff(marker,fname[0]+"mark");
//                       //(marker.getImageStack(),tmp.getImageStack());
//                        //use the marker stack and tmp to get the geodesic
//                        //savegeodesic
//                }
//    }

    private void makeMeasurements(ArrayList<ArrayList<ImagePlus>> brainImage, ArrayList results1) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
