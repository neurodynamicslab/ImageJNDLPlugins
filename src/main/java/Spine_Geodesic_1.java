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
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.Resizer;
import ij.plugin.Slicer;
import ij.plugin.SubstackMaker;
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
import inra.ijpb.binary.distmap.ChamferMask3DW6;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3D;
import inra.ijpb.binary.geodesic.GeodesicDistanceTransform3DFloat;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.skeletonize3D.*;

/**
 * A plugin for measuring the inter spine distances from a geodesic map. Each of the dendrite is
 * is identified by a unique number. The spine image is used to generate measurement mask. This is mask is later used on the 
 * geodesic distance image to obtain the spines with their distance measured from internal reference point in each of the dendrite. 
 */
public class Spine_Geodesic_1 implements PlugIn{
	protected ImagePlus image;
    // image property members

	// plugin parameters
	public double value;
	public String name;
        
        ArrayList <Thread> monitor = new ArrayList();
        int threadCount;
        ArrayList<String> dendriteSels = new ArrayList();
        ArrayList <File> coOrdSels = new ArrayList();
        ArrayList<String> errFile = new ArrayList();
    ///*this has 2 elements only one for dendrite sel image and other for coresponding spine selection*/
    /*<JTable or JList or ArrayList<String>>*/
    // for storing the measurements from the images
        
        ConcurrentHashMap Dendrites = new ConcurrentHashMap();
        
        ArrayList <SpineDescriptor> SpineData = new ArrayList();
        MultiFileDialog dendFileDialog = new MultiFileDialog(null,true);
        File dendFileList,spineFileList ;  //csv file containing the list of files with dentrites
        static String startDirectory;
        int roiWidth = 2 ;// ROI width over which to search for max the geodesic distance
        private final boolean inclDepth = true; //set this to true for searching for max geodesic dist in Z
        private ArrayList roiList;
        private MultiFileDialog coOrdFilesDialog = new MultiFileDialog(null,true);
        

//	@Override
//	public int setup(String arg, ImagePlus imp) {
////		if (arg.equals("about")) {
////			showAbout();
////			return DONE;
////		}
////
////		image = imp;
//		return ;//DOES_8G | DOES_16 | DOES_32 | DOES_RGB | DOES_STACKS;
//	}

	@Override
	public void run(String str) {
                
                //Open a multi file doalog and get a list of files to work on.
                int result;
                String [] coOrdFiles, selection;
                
                int option = javax.swing.JOptionPane.showConfirmDialog(null,"Do you want to use a file list from a csv file ?","Choose the mode of data entry",javax.swing.JOptionPane.YES_NO_CANCEL_OPTION);
                if(option == javax.swing.JOptionPane.YES_OPTION){
                    JFileChooser fc = new JFileChooser();
                    fc.setDialogTitle("Select the csv file with list of imagefiles having dendrite IDs ");
                    result = fc.showOpenDialog(null);
                    
                    if(result == JFileChooser.APPROVE_OPTION){
                        try {
                            populateFileLists();
                        } catch (IOException ex) {
                            Logger.getLogger(Spine_Geodesic_1.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }else{
                        javax.swing.JOptionPane.showMessageDialog(null, "You need to choose a file for me to process");
                        return;
                    }
                }else if(option == javax.swing.JOptionPane.NO_OPTION){
                     if(startDirectory != null && !startDirectory.isEmpty())   
                        dendFileDialog.setStartDirectory(new File(startDirectory));
                    dendFileDialog.setTitle("Please select the image files with identified dendrites");
                    this.dendFileDialog.setVisible(true);
                    selection = dendFileDialog.getSelectionArray();
                    
                    
                    if(selection.length == 0 || dendFileDialog.getResult() != 2){
                        //user did not select any dendrite file so no point 
                        //opting for co ord file
                        return;
                    }
                    else{
                        startDirectory = dendFileDialog.getDirectory();
                        coOrdFilesDialog.setStartDirectory(new File(startDirectory));
                        
                        coOrdFilesDialog.setVisible(true);
                        coOrdFiles = coOrdFilesDialog.getSelectionArray();                  
                    }
                    ImagePlus dendImg;
                    File temp;
                    int count = 0;
                    
                    
                    for(String fName : selection){
                        
                        dendImg = new ImagePlus(fName);
                        temp = new File(coOrdFiles[count]);
                        
                        if(dendImg != null && temp.isFile()){
                            this.dendriteSels.add(fName);
                            this.coOrdSels.add(temp);
                            dendImg.close();
                        }else{
                            this.errFile.add(fName);
                        }
                        count++;
                    }
    //                    }
                     
                }else if(option == javax.swing.JOptionPane.CANCEL_OPTION){  //Interpreting this as test
                    
                    JFileChooser FC = new JFileChooser();
                    FC.setMultiSelectionEnabled(false);
                    
                    int FCres = FC.showOpenDialog(null);
                    
                    if (FCres != JFileChooser.CANCEL_OPTION){
                        
                        File Imagefile = FC.getSelectedFile();
                        if(Imagefile.isFile()){
                            ImagePlus imageIn = new ImagePlus(Imagefile.getAbsolutePath());
                            if(imageIn != null){
                                 imageIn = this.expandZ(imageIn, 3);
                                 imageIn.setTitle("Expanded");
                                 imageIn.show();
                                 
                                 ImagePlus tmp = this.subSampleZ(imageIn, 3);
                                 imageIn.setTitle("Subsampled");
                                 tmp.show();
                            }else
                                return;
                                
                        }
                                
                      
                    }
                    
                    
                }
                
                
                this.measureDends();

                 
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
		//ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		//image.show();

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
        private ImagePlus convert2geodesic( String sourceImg,String destsuffix){
            ImagePlus img = new ImagePlus (sourceImg);
            if (img == null)
                return img;
            //ArrayList success, failure;
            //success = this.dendriteSels;
            //failure = this.errFile;
           
           //Place holder for expansion
          
           //img = this.expandZ(img, 3.0);
          
            
            double ijMem = (IJ.maxMemory() - IJ.currentMemory())/1000000;
            
            double fileSz = img.getSizeInBytes()/1000000;
            System.out.println("Available Memory is :" + ijMem + "File size is:" + fileSz);
            if (ijMem < 2 * fileSz){
              
                System.gc();
                ijMem = IJ.maxMemory() - IJ.currentMemory();
                System.out.println("Not enough memory: "+ ijMem + " MB but File Sz is : " + fileSz  + "(MB) Diff : " + (ijMem - fileSz));
            }
            
            //SwingWorker worker = new SwingWorker(){
               // @Override
               // protected Object doInBackground() throws Exception {
                   
                            ThresholdToSelection roiCreator = new ThresholdToSelection();
                            ChamferMask3D chamferMask;
                            
                            chamferMask = ChamferMask3D.SVENSSON_3_4_5_7; //new ChamferMask3DW6(10,14,17,2,24,30);

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
                                    ImagePlus mask = new ImagePlus();
                                    mask.setStack(maskStk);
                                    //mask = expandZ(mask,3);
                                    mask = this.prepareMask(mask);
                                    maskStk = mask.getImageStack();
                                    markStk = maskStk.duplicate();
                                    int newDepth = markStk.getSize(); // this will reflect if the changed depth
                                    for (int count = 1 ; count <= newDepth ; count++){
                                        markStk.getProcessor(count).convertToByteProcessor();
                                        markStk.getProcessor(count).set(0);
                                    }
                                    markStk.getProcessor((minSqinSlice-startZ)).putPixelValue(closePoint.x-bRect.x,closePoint.y-bRect.y, 255);
                                    
                                    boolean normaliseWeights = false;
                                    GeodesicDistanceTransform3D algo = new GeodesicDistanceTransform3DFloat(chamferMask, normaliseWeights);
                                    DefaultAlgoListener.monitor(algo);
                                    
                                    

                                    // Compute distance on specified images

                                    result = algo.geodesicDistanceMap(markStk, maskStk);
                                    //ImagePlus temp = new ImagePlus();
                                    //temp.setStack(result);
                                    //result = this.subSampleZ(temp, 3).getImageStack();
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
                            //String sourceFileName = sourceImg.getFileInfo().getFilePath();
                            
                            //out =this.subSampleZ(out,3);    
                            boolean fileStatus = IJ.saveAsTiff(out, sourceImg.substring(0, sourceImg.lastIndexOf("."))+ destsuffix);
                            
                           ///Place holder for z- shrinking code
                            //out =this.subSampleZ(out,3);           
                            
                            if(fileStatus){
                                System.out.println("File :"+sourceImg +" processed");
                                //success.add(sourceImg.getFileInfo().getFilePath());
                                return out;
                            }
                            else{
                                System.out.println("Error writing File :"+sourceImg.substring(0, sourceImg.lastIndexOf(".")-1) + destsuffix);
                                //failure.add(sourceImg);
                                return null;
                            }
                            //img.setStack(resStk);
                //            out.setStack(markStk);
                //            IJ.saveAsTiff(out, fname+"_mrk");
           //         return out;
            //    }
               
                
            //};
            //Thread tp = new Thread(worker,"gesodesic_"+threadCount);
            //worker.execute();
            //tp.start();
            
            //monitor.add(tp);
            //threadCount++;
            
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
        private void measureDends() {
            
        
            int nFiles = dendriteSels.size();
            String pathName, rootName;
            String datetime; 
            
            if( dendriteSels != null && coOrdSels != null && nFiles != coOrdSels.size())
                return;
            
            ImagePlus dendID, geoImg;
            FileReader cordFile ;
            FileWriter outFile;
            FileWriter resFile,sumFile;
            try {
                for(int count = 0 ; count < nFiles ; count ++){


                        //dendID = new ImagePlus (dendriteSels.get(count));
                        //dendID.getFileInfo().directory = dendriteSels.get(count);
                        geoImg = convert2geodesic(dendriteSels.get(count),"geo1");//new ImagePlus(geoFileNames[count]);
                        //dendID.close();
                        
                        pathName = coOrdSels.get(count).getAbsolutePath();
                        String fName = coOrdSels.get(count).getName();
                        if(!fName.isEmpty())
                           fName = fName.substring(0,fName.lastIndexOf("."));
                        rootName = pathName.substring(0,pathName.lastIndexOf(File.separator));
                        datetime = timeTaggedFolderNameGenerator();                             //provides a name with the date and month 
                                                                                                // in the following format (dd Mon File separator hh_mm)
                        String resDir = rootName + File.separator+"res" + datetime;
                        
                        File testDir = new File(resDir);
                        if(!testDir.exists())
                              testDir.mkdirs();
                        resDir += File.separator;        
                        cordFile = new FileReader(pathName);
                        outFile =  new FileWriter(resDir+fName+"_res.txt");
                        resFile = new FileWriter(resDir+"_skl.txt");
                        sumFile = new FileWriter(resDir+"_summary.txt");
                        
                        
                        dendID = new ImagePlus (dendriteSels.get(count));
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
                        System.out.print("Finished the skeleton\n");
                        resFile.write("TreeID\tTotalVoxels\tnBranches\tAveLength\tnJunctions\tTotLen\n");
                        resFile.write(outPut);
                        resFile.close();
                        
                        temp.close();
                        //temp.show();
                        

                        ArrayList<Roi> rois  = getRois(cordFile);
                        System.out.print("Finished reading ROIs. There are "+ rois.size()+" spines \n");
                        ArrayList<String> result = doMeasurement(rois,dendID,geoImg);
                        writeResult(result,outFile);
                        outFile.close();
                        createSummary(sumFile);
                        sumFile.close();
                        dendID.close();
                        geoImg.close();
                        cordFile.close();
                }
            }catch (FileNotFoundException ex) {
                    Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
            }catch (IOException ex) {
                Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
    }

    private String timeTaggedFolderNameGenerator() {
        LocalDateTime time = LocalDateTime.now();
        int dayOfMon = time.getDayOfMonth();
        String dOMon = (dayOfMon > 9 ) ? ""+dayOfMon : "0"+dayOfMon;
        String datetime = ""+dOMon +time.getMonth().toString().substring(0, 3)+File.separator + time.getHour()+"_"+time.getMinute();
        return datetime;
    }

    private ArrayList<Roi> getRois(FileReader cordFile) {
            
            ArrayList rois = new ArrayList();
            try {
                //        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
                BufferedReader reader = new BufferedReader(cordFile);
                String ln = reader.readLine();
               // System.out.println(ln);
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
              slice = slice <= dendID.getNSlices() ? slice : slice - 1;             //should not be required
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
              if(this.inclDepth){
                //dist = findMaxdist(slice, dendID, roi, ID, geoImg, dist);
                
              }
                  
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
            outRow += "Spine Type\t";
            outRow += "x" +"\t";
            outRow += "y" +"\t";
            outRow += "z" + "\t";
            outRow += "Density Normalised Neigh" +"\t";
            outRow += "Spine density \t";
            outRow += "Dendritic Length \n";
                        
            try{
                            w.write(outRow);
                        }catch(IOException ex){
                            
                        }                                       ///Write the above header for the data table in the text file
                        
            for (Iterator<ConcurrentHashMap.Entry<Float,ArrayList>> it = Dendrites.entrySet().iterator(); it.hasNext();){
                
                ConcurrentHashMap.Entry<Float,ArrayList> entry = it.next();
                Collections.sort(entry.getValue(), new distComparator());
                
            //Calculate the nearest neighbour distance and print to file
                
                ArrayList<SpineDescriptor> distSorted = entry.getValue();       //Length of this (number of elements) is the number of spines
                                                                                // The geo distance of the last spine in this list is the length of the dendrite
                                                                              // It is approximate to the extent that the dendrite does not extend too much beyond this
                //float denLength = distSorted.getLast().getDistFromIdx();
                float noSpines = distSorted.size();
                float denLength = distSorted.get((int)(noSpines-1)).getDistFromIdx();
                float spineDen = noSpines/denLength;
                
                SpineDescriptor prevSp = null,nextSp = null;
                int nextIdx = 0;
                int totalSp = distSorted.size();
                float prevDist = 0, nextDist = 0, cartDist = 0;
                
                for(SpineDescriptor spine : distSorted){            //iterate thru a dendrite spine by spine
                    nextIdx++;
                    if(prevSp == null){
                        prevSp = spine;
                        if(nextIdx < totalSp){                               
                                nextSp = distSorted.get(nextIdx);
                                nextDist = nextSp.getDistFromIdx()- spine.getDistFromIdx(); //distance to the next spine
                                cartDist = measureCartDist(nextSp,spine); //Calcualte the cart distance and compare
                                //nextDist = (cartDist < nextDist )? nextDist : -cartDist;      //-ive sign is to identify the incorrect dist       
                                spine.setNearNeighDist(nextDist);
                                spine.setFarthestNeighDist(nextDist);
                                spine.setTypeFlag(SpineDescriptor.spineType.First);
                        }else{
                                spine.setTypeFlag(SpineDescriptor.spineType.Singleton);
                        }
                    }else{
                        prevDist = spine.getDistFromIdx() - prevSp.getDistFromIdx();  //distance from the previous spine
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
                                //prevDist = (cartDist < prevDist)? prevDist : -cartDist;
                                spine.setNearNeighDist(prevDist);
                                spine.setFarthestNeighDist(nextDist);
                            }
                        }else{
                            //Calcualte the cart distance and compare
                            cartDist = measureCartDist(prevSp,spine);
                            //prevDist = (cartDist < prevDist)? prevDist : -cartDist;
                            spine.setNearNeighDist(prevDist);
                            spine.setFarthestNeighDist(prevDist);
                            
                            if (spine.getTypeFlag() == SpineDescriptor.spineType.First) 
                                    spine.setTypeFlag(SpineDescriptor.spineType.Singleton) ;
                             else
                                spine.setTypeFlag(SpineDescriptor.spineType.Last);
                        }
                    }
                    
                    
                    outRow = entry.getKey() +"\t";
                    outRow += spine.getSpineID() + "\t";
                    outRow += spine.getDistFromIdx() +"\t";
                    outRow += cartDist +"\t";
                    outRow += spine.getNearNeighDist() +"\t";
                    outRow += spine.getTypeFlag().name() +"\t";
                    Rectangle b = spine.getBound();
                    outRow += (b != null)?b.x +"\t" : "\t";
                    outRow += (b != null)?b.y +"\t" : "\t";
                    outRow += spine.getzPosition() + "\t";
                    outRow += spine.getNearNeighDist()*spineDen +"\t";
                    outRow += spineDen + "\t";
                    outRow += denLength + "\n";
                    try {
                        w.write(outRow);
                    } catch (IOException ex) {
                        Logger.getLogger(Spine_Geodesic.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    prevSp = spine;
                }
                    //Tabulate the results             
            }
            
            
    }

    private void populateFileLists() throws FileNotFoundException, IOException {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        BufferedReader fileReader = new BufferedReader ( new FileReader(this.dendFileList.getAbsolutePath()));
        String lineData;
        String delimiter = ","; 
        String[] fNames ;
        ImagePlus dendFile;
        File roiFile;
            
        while ((lineData = fileReader.readLine())!= null){
             
             fNames = lineData.split(delimiter);
             dendFile = new ImagePlus(fNames[1]);
             roiFile = new File(fNames[2]);
             if(dendFile != null ){
                if(!roiFile.isFile()){
                    this.dendriteSels.add(fNames[1]);
                    this.coOrdSels.add(roiFile);
                    dendFile.close();
                }else{
                    this.errFile.add(fNames[2]);
                }
             }else{
                 this.errFile.add(fNames[1]);
             }
        }   
    }

    private ImagePlus expandZ(ImagePlus img, double d) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        int curDep = img.getImageStack().size();
        int newDepth = (int)(curDep * d);
        Resizer resizer = new Resizer();
        ImagePlus outPut = resizer.zScale(img, newDepth, ImageProcessor.NONE);
        //ImagePlus outPut = slicer.reslice(img);
        
        //ij.Prefs.set("AVOID_RESLICE_INTERPOLATION",true);
//        ij.Prefs.avoidResliceInterpolation = true;
//        ij.Prefs.savePreferences();
//        cal.pixelDepth = 1.0;
//        outPut.setCalibration(cal);
//
//        outPut = slicer.reslice(outPut);
        
        return outPut;
    }

    private ImagePlus subSampleZ(ImagePlus out, int i) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        
        SubstackMaker stkMaker  = new SubstackMaker();
        int maxSlice  = out.getStack().size();
        String cmd = "1-"+maxSlice+"-"+i;
            
        
        return stkMaker.makeSubstack(out, cmd);
    }

    private ImagePlus prepareMask(ImagePlus mask) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
  
        ImageStack cleanStack = mask.getImageStack();
       
        int nSlices = cleanStack.getSize();
        ImageProcessor ip;
        ByteProcessor clnMask;
        
        for(int count = 1 ; count <= nSlices ; count++){
            
            ip = cleanStack.getProcessor(count);
            ip.setThreshold(1, 255);
            clnMask = ip.createMask();
            cleanStack.setProcessor(clnMask, count);
        }
        
        mask.setStack(cleanStack);
        return mask;
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
