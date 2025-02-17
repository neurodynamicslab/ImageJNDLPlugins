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
import ij.process.StackConverter;
import ij.process.StackStatistics;
import java.awt.Rectangle;
import java.util.ArrayList;

import inra.ijpb.algo.DefaultAlgoListener;
import inra.ijpb.binary.distmap.ChamferMask3D;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3D;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3DFloat;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.SkeletonResult;

import sc.fiji.skeletonize3D.*;

/**
 * A plugin for measuring the inter spine distances from a geodesic map. Each of the dendrite is
 * is identified by a unique number. The spine image is used to generate measurement mask. This is mask is later used on the 
 * geodesic distance image to obtain the spines with their distance measured from internal reference point in each of the dendrite. 
 */
public class Spine_Geodesic implements PlugInFilter {
	protected ImagePlus image;
    // image property members

	// plugin parameters
	public double value;
	public String name;
        
        ArrayList <Thread> monitor = new ArrayList();
        int threadCount;
        ArrayList<ImagePlus> dendriteSels;
        ArrayList<String> errFile;
    ///*this has 2 elements only one for dendrite sel image and other for coresponding spine selection*/
    /*<JTable or JList or ArrayList<String>>*/
    // for storing the measurements from the images
        
        ConcurrentHashMap Dendrites = new ConcurrentHashMap();
        
        ArrayList <SpineDescriptor> SpineData = new ArrayList();
        MultiFileDialog FD = new MultiFileDialog(null,true);
        static String startDirectory;
        int roiWidth = 2 ;// ROI width over which to search for max the geodesic distance
        private final boolean inclDepth = true; //set this to true for searching for max geodesic dist in Z
        
        

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
                
                int option = javax.swing.JOptionPane.showConfirmDialog(null,"Do you to measure ?");
                if(option == javax.swing.JOptionPane.OK_OPTION)
                    this.makeMeasurements();
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
                            }else{
                                System.out.println(monitor.get(count).getName() + "is alive");
                            }
                        }
                        if(activeCount > 0) 
                                    System.out.println("Waiting for "+activeCount+ " threads to end out of " + monitor.size());
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
            long availableMem = java.lang.Runtime.getRuntime().freeMemory();
            long ijMem = IJ.maxMemory() - IJ.currentMemory();
            double fileSz = img.getSizeInBytes();
            System.out.println("Available Memory is :" + ijMem + "File size is:" + fileSz);
            while (ijMem < 2 * fileSz){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    //Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
                }
                System.gc();
                ijMem = IJ.maxMemory() - IJ.currentMemory();
                System.out.println("Waiting for memory to clear.."+ ijMem + " File Sz : " + (long)fileSz  + "Diff : " + (ijMem - fileSz));
            }
            
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

//  

    /**
     * A function to estimate the geodesic distance of the spines.
     * Logic: Open the images with i) dendrite identities ii) dendrite geodesic and iii) table with spine centers (.csv /.txt files).
     * Generate ROI from the object X Y and Z )co -ordinates (from spine centers).
     * Measure the intensity of these ROIs in dendrite identities and in geodesics images.These along with X,y,Z co-ordinates need to be written to the output file (.csv)
     * @param brainImage
     * @param results1 
     */
        private void makeMeasurements() {
            
            
            String[] dendFileNames,geoFileNames,measFileNames;
        
            MultiFileDialog dendFiles = new MultiFileDialog (null, true);
            dendFiles.setTitle("Select image files with dendrite ID");
            File start = null;                    
            
            if (startDirectory != null)
                dendFiles.setStartDirectory(new File(startDirectory));
            dendFiles.setVisible(true);
            dendFileNames = dendFiles.getSelectionArray();
            startDirectory = dendFiles.getDirectory();
            start = new File(startDirectory);
           
            MultiFileDialog geoFiles = new MultiFileDialog(null,true,start);
            MultiFileDialog measurements = new MultiFileDialog(null,true,start);
            
            geoFiles.setTitle("Select the image files with geodesic distances");
            measurements.setTitle("Select the csv file with co-ordinates");
            
            geoFiles.getFileSelDialog().setCurrentDirectory(start);
            geoFiles.setVisible(true);
            geoFileNames = geoFiles.getSelectionArray();
            
            measurements.getFileSelDialog().setCurrentDirectory(start);
            measurements.setVisible(true);
            measFileNames =  measurements.getSelectionArray();
            
            if( dendFileNames.length != geoFileNames.length || geoFileNames.length != measFileNames.length)
                return;
            int nFiles = geoFileNames.length;
            ImagePlus dendID, geoImg, sklImg;
            String [] sklFileNames = new String[nFiles];
            FileReader cordFile ;
            FileWriter outFile;
            FileWriter resFile,sumFile;
            try {
                for(int count = 0 ; count < nFiles ; count ++){


                        dendID = new ImagePlus(dendFileNames[count]);
                        geoImg = new ImagePlus(geoFileNames[count]);
                        
                        String rootName = measFileNames[count].split("\\.")[0];
                        cordFile = new FileReader(measFileNames[count]);
                        outFile =  new FileWriter(rootName+"_res.txt");
                        resFile = new FileWriter(rootName+"_skl.txt");
                        sumFile = new FileWriter(rootName+"_summary.txt");
                        
                        
                        
                        //dendID.show();
                        ImagePlus temp = dendID.duplicate();
                        StackConverter converter = new StackConverter(temp);
                        converter.convertToGray8();
                        
                        Skeletonize3D_ skeleton = new Skeletonize3D_();
                        skeleton.setup("", temp);
                        skeleton.run(temp.getProcessor());
                        
                        AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();
                        analyser.setup("", temp);
                        SkeletonResult resultSkl = analyser.run(AnalyzeSkeleton_.NONE, false,false,null, true, false);
                        int nTrees = resultSkl.getNumOfTrees();
                        String outPut = "";
                        int[] totalVoxels   = resultSkl.calculateNumberOfVoxels();
                        int[] nBranches     = resultSkl.getBranches();
                        double[] aveLeng    = resultSkl.getAverageBranchLength();
                        int[] nJunctions    = resultSkl.getJunctions();
                        
                        for(int tCount = 0 ; tCount < nTrees ; tCount++){
                            
                            outPut  += tCount + "\t";
                            
                            outPut += totalVoxels[tCount] + "\t";
                            outPut += nBranches[tCount] +"\t";
                            outPut += aveLeng[tCount] + "\t";
                            outPut += nJunctions[tCount] +"\t";
                            outPut += nBranches[tCount]*aveLeng[tCount]+"\n";
                            
                        }
                        System.out.print("Finished the skeleton");
                        resFile.write("TreeID\tTotalVoxels\tnBranches\tAveLength\tnJunctions\tTotLen\n");
                        resFile.write(outPut);
                        resFile.close();
                        //temp.show();
                        

                        ArrayList<Roi> rois  = getRois(cordFile);
                        System.out.print("Finished reading ROIs. There are "+ rois.size()+" spines \n");
                        ArrayList<String> result = doMeasurement(rois,dendID,geoImg);
                        writeResult(result,outFile);
                        outFile.close();
                        createSummary(sumFile);
                        sumFile.close();
                }
            }catch (FileNotFoundException ex) {
                    Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
            }catch (IOException ex) {
                Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
    }

    private ArrayList<Roi> getRois(FileReader cordFile) {
            
            ArrayList rois = new ArrayList();
            try {
                //        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
                BufferedReader reader = new BufferedReader(cordFile);
                String ln = reader.readLine();
                System.out.println(ln);
                if(ln == null )
                    return null;
                while( (ln = reader.readLine()) != null){
                    float xf =  Float.parseFloat(ln.split(",")[8]) - roiWidth/2;
                    float yf =  Float.parseFloat(ln.split(",")[9]) - roiWidth/2;
                    float zf =  Float.parseFloat(ln.split(",")[10]);
                    int position = zf < 1 ? 1 : Math.round(zf);
                    xf = (xf < 0 ) ? 0 : xf;
                    yf = (yf < 0 ) ? 0 : yf;
                    Roi roi = new Roi(xf,yf,roiWidth,roiWidth);
                    roi.setPosition(position);
                    rois.add(roi);
                }               
            } catch (IOException ex) {
                Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            return rois;
    }

    private ArrayList<String> doMeasurement(ArrayList<Roi> rois, ImagePlus dendID, ImagePlus geoImg) {
//        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
          ArrayList Output = new ArrayList();
          String result;
          Rectangle rect;
          int count = 1 ,slice;
          float ID, dist;
          //ArrayList denDist;
          for( Roi roi : rois){
              rect = roi.getBounds();
              slice  = roi.getPosition();
 //             slice = slice <= dendID.getNSlices() ? slice : slice - 1;             //should not be required
              ImageProcessor ipD = dendID.getStack().getProcessor(slice);
              ipD.setRoi(roi);
              ID = (float)ipD.getStats().max;
              if(ID == 0)               
                  continue;                             //Add code to collect the "false spines"  selected as spines but not as dendrites. due to size thld.
              //float ID  = dendID.getStack().getProcessor(slice).getPixelValue(rect.x, rect.y);
              //float dist = geoImg.getStack().getProcessor(slice).getPixelValue(rect.x,rect.y);
              ImageProcessor ipG = geoImg.getStack().getProcessor(slice);
              ipG.setRoi(roi);
              dist = (float)ipG.getStats().max;
              if(this.inclDepth)
                dist = findMaxdist(slice, dendID, roi, ID, geoImg, dist);
                  
              result = count +"\t" +rect.x + "\t" + rect.y +"\t"+ slice + "\t" + ID + "\t" + dist + "\n";
              //Add the dend ID and dist to 
//              denDist = (ArrayList)Dendrites.get(ID);
//              if(denDist == null)
//                  denDist = new ArrayList();
//              denDist.add(dist);
              
              //Dendrites.put(ID,denDist);
              SpineData = (ArrayList)Dendrites.get(ID);
              if(SpineData == null)
                  SpineData = new ArrayList<SpineDescriptor>();
              SpineDescriptor spine = new SpineDescriptor(Math.round(ID),count,rect,dist);
              spine.setBound(rect);
              spine.setzPosition(slice);
              //spine.setDistFromIdx(dist);
              SpineData.add(spine);
              Dendrites.put(ID, SpineData);
              
              Output.add(result);
              count++;
          }
         
          return Output;
    }

    private float findMaxdist(int slice, ImagePlus dendID, Roi roi, float ID, ImagePlus geoImg, float dist) {
        ImageProcessor ipD;
        ImageProcessor ipG;
        for(int lwSlice = slice - this.roiWidth/2 ; lwSlice < slice  && lwSlice > 0; lwSlice++){
            ipD = dendID.getStack().getProcessor(lwSlice);
            ipD.setRoi(roi);
            float id = (float) ipD.getStats().max;
            if( id == ID){
                ipG = geoImg.getStack().getProcessor(lwSlice);
                ipG.setRoi(roi);
                float d  = (float) ipG.getStats().max;
                dist = (d > dist)? d: dist;
            }
        }
        int maxSlice = dendID.getNSlices();
        int tpSlice = slice + roiWidth/2;
        tpSlice = (tpSlice < maxSlice)? tpSlice:maxSlice;
        for(int cSlice = slice + 1 ; cSlice < tpSlice ; cSlice++){
            ipD = dendID.getStack().getProcessor(cSlice);
            ipD.setRoi(roi);
            float id = (float) ipD.getStats().max;
            if( id == ID){
                ipG = geoImg.getStack().getProcessor(cSlice);
                ipG.setRoi(roi);
                float d  = (float) ipG.getStats().max;
                dist = (d > dist)? d: dist;
            }
        }
        return dist;
    }
    private void createSummary(FileWriter w){
            String outRow;
            outRow = "DendriteID" +"\t";
                        outRow += "SpineId" + "\t";
                        outRow += "GeoDistfromMarker" +"\t";
                        outRow += "Cart Dist to Neigh" +"\t";
                        outRow += "Near Neigh GeoDes" +"\t";
                        outRow += "x" +"\t";
                        outRow += "y" +"\n";
                        
                        try{
                            w.write(outRow);
                        }catch(IOException ex){
                            
                        }
                        
            for (Iterator<ConcurrentHashMap.Entry<Float,ArrayList>> it = Dendrites.entrySet().iterator(); it.hasNext();){
                    ConcurrentHashMap.Entry<Float,ArrayList> entry = it.next();
                    Collections.sort(entry.getValue(), new distComparator());
                    //Calculate the nearest neighbour distance and print to file
                    ArrayList<SpineDescriptor> distSorted = entry.getValue();
                    SpineDescriptor prevSp = null,nextSp;
                    int nextIdx = 1;
                    int totalSp = distSorted.size();
                    float prevDist, nextDist, cartDist = 0;
                    for(SpineDescriptor spine : distSorted){
                        nextIdx++;
                        if(prevSp == null){
                            prevSp = spine;
                            if(nextIdx < totalSp){                               
                                nextSp = distSorted.get(nextIdx);
                                nextDist = nextSp.getDistFromIdx()- spine.getDistFromIdx();
                                //cartDist = measureCartDist(nextSp,spine); //Calcualte the cart distance and compare
                                //nextDist = (cartDist < nextDist )? nextDist : -cartDist;      //-ive sign is to identify the incorrect dist       
                                spine.setNearNeighDist(nextDist);
                                spine.setFarthestNeighDist(nextDist);
                            }
                        }
                        else{
                            prevDist = spine.getDistFromIdx() - prevSp.getDistFromIdx();
                            if (nextIdx < totalSp){
                                nextSp = distSorted.get(nextIdx);
                                nextDist = nextSp.getDistFromIdx()- spine.getDistFromIdx();
                                if( prevDist > nextDist){
                                    //Calcualte the cart distance and compare
                                    cartDist =  measureCartDist(nextSp,spine);
                                    //nextDist = (cartDist < nextDist)? nextDist : -cartDist;
                                    spine.setNearNeighDist(nextDist);
                                    spine.setFarthestNeighDist(prevDist);
                                }else{
                                    //Calcualte the cart distance and compare
                                    cartDist = measureCartDist(prevSp,spine);
                                    //prevDist = (cartDist < prevDist)? prevDist : -prevDist;
                                    spine.setNearNeighDist(prevDist);
                                    spine.setFarthestNeighDist(nextDist);
                                }
                            }else{
                                //Calcualte the cart distance and compare
                                cartDist = measureCartDist(prevSp,spine);
                                //prevDist = (cartDist < prevDist)? prevDist : -prevDist;
                                spine.setNearNeighDist(prevDist);
                                spine.setFarthestNeighDist(prevDist);
                            }
                        }
                        outRow = entry.getKey() +"\t";
                        outRow += spine.getSpineID() + "\t";
                        outRow += spine.getDistFromIdx() +"\t";
                        outRow += cartDist +"\t";
                        outRow += spine.getNearNeighDist() +"\t";
                        Rectangle b = spine.getBound();
                        outRow += (b != null)?b.x +"\t" : "-\t";
                        outRow += (b != null)?b.y +"\n" : "-\n";
                        try {
                            w.write(outRow);
                        } catch (IOException ex) {
                            Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    //Tabulate the results
                    
            }
            
            
    }
    class distComparator implements Comparator<SpineDescriptor>{

        @Override
        public int compare(SpineDescriptor s1, SpineDescriptor s2) {

            return Float.compare(s1.getDistFromIdx(), s2.getDistFromIdx());
        }
    
    }
    private float measureCartDist(SpineDescriptor s1, SpineDescriptor s2){
        
        Rectangle b1 = s1.getBound(),b2 = s2.getBound();
        double zSq = 0.0;
        if( s1.getzPosition() != -1 && s2.getzPosition() != -1)
            zSq = Math.pow((s1.getzPosition()-s2.getzPosition()), 2);
        return (float)Math.pow((Math.pow((b1.x - b2.x),2) + Math.pow((b1.y - b2.y),2) + zSq ),0.5);
        
    }
    private void writeResult(ArrayList<String> result, FileWriter outFile) {
//        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody

              String header = "ID" +"\t" +"X Ord"+ "\t" + "Y Ord" +"\t"+ "Z" + "\t" + "DentID" + "\t" + "Geodist" + "\n";
            try {
                outFile.write(header);
            } catch (IOException ex) {
                Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
            }
              try {
                  for(String ln : result)
                        outFile.write(ln);
                  outFile.close();
              } catch (IOException ex) {
                  Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
              }
              
    }
    private void writeSpineDist(File out){
        
//        Iterator iter = Dendrites.entrySet().iterator();
//        Map.Entry entry;
//        ArrayList<Float> arrayTowrite;
//        try{
//            FileWriter outFile = new FileWriter(out);
//            while(iter.hasNext()){
//                entry = (Map.Entry<Integer, ArrayList>)iter.next();
//                arrayTowrite = (ArrayList)entry.getValue();
//                Collections.sort(arrayTowrite);
//                for(Float val : arrayTowrite)
//                    outFile.write(""+(Integer)entry.getKey()+"\t"+val+"\n");
//                        }
//        }catch(IOException ex){
//            
//        }
    }
}
