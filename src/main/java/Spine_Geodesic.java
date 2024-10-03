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
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * A plugin for measuring the inter spine distances from a geodesic map. Each of the dendrite is
 * is identified by a unique number. The spine image is used to generate measurement mask. This is mask is later used on the 
 * geodesic distance image to obtain the spines with their distance measured from internal reference point in each of the dendtrite. 
 */
public class Spine_Geodesic implements PlugInFilter {
	protected ImagePlus image;

	// image property members
	private int width;
	private int height;

	// plugin parameters
	public double value;
	public String name;
        
        ArrayList dendriteSels, spineSels,errFile;
        
        ArrayList <ArrayList<ImagePlus>> brainImage = new ArrayList(); ///*this has 2 elements only one for dendrite sel image and other for coresponding spine selection*/
        ArrayList results = new ArrayList(); // for storing the measurements from the images
        
        MultiFileDialog FD = new MultiFileDialog(null,true);

        
        

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		image = imp;
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB | DOES_STACKS;
	}

	@Override
	public void run(ImageProcessor ip) {
                
                //Open a multi file doalog and get a list of files to work on.
                String[] dendfNames,spinefNames;
                boolean errStatus;
               
                FD.setTitle("Select the files with dendritic selections");
                FD.setVisible(true);
                
                errStatus =  FD.getResult()== 2 ; //selection is made and has atleast one file
                
                if (!errStatus)
                        dendfNames = FD.getSelectionArray();
                else
                    return;
                
                FD.setTitle("Select the files with spine selections (in the same order)");
                FD.setVisible(true);
                
                errStatus =  FD.getResult() == 2 ;
                
                if (! errStatus){
                   
                    spinefNames = FD.getSelectionArray();
                    
                    errStatus =  !( spinefNames.length == dendfNames.length);
                    
                    if(errStatus)
                        return;
                    
                    //TODO : show the paired fnames in a dual window list and allow the user to change the pairing
                    int fCount = 0;
                    ImagePlus tmp;
                    dendriteSels = new ArrayList();
                    errFile = new ArrayList();
                    
                    for (String name : dendfNames){
                        //TODO: Add check for 
                        tmp = new ImagePlus(name);
                        if(tmp != null)
                            dendriteSels.add(tmp);
                        else
                            errFile.add(name);
                    }
                    convert2geodesic(dendriteSels);
                    fCount  = 0;
                    for (Iterator it = dendriteSels.iterator(); it.hasNext();) {
                        ImagePlus imp = (ImagePlus) it.next();
                        String destname = dendfNames[fCount];
                        if (! errFile.contains(destname)){
                            destname = destname.split("\\.")[0];
                            destname += "_geo.tiff";
                            IJ.saveAsTiff(imp, destname);
                            
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

	private boolean showDialog() {
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
	}

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
	public void process(ImagePlus image) {
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

		// open the Clown sample
		ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}

    private void convert2geodesic(ArrayList dendriteSels) {
        //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        
                for (Object o : dendriteSels){
                    ImagePlus tmp = (ImagePlus)o;
                    
                    
                    //get the minimum and maximum intensity to identify the number of objects
                    //select out individual intensities from min to max
                    //identify the start pixel (left top ?) as marker for each object
                    //generate marker image (invert the mask and set the marker pixel as 1)
                    //run the geodesic
                    
                    
                    
                }
    }

    private void makeMeasurements(ArrayList<ArrayList<ImagePlus>> brainImage, ArrayList results1) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
