package com.playsawdust.chipper.glow.image;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.playsawdust.chipper.glow.image.io.PNGImageWriter;
import com.playsawdust.chipper.glow.image.io.TiffImageInput;
import com.playsawdust.chipper.glow.image.vector.Vec2l;
import com.playsawdust.chipper.glow.io.DataSlice;

public class Test {
	public static void main(String[] args) {
		try {
			RandomAccessFile raf = new RandomAccessFile("test.tif", "r");
			//RandomAccessFile raf = new RandomAccessFile("test_small.tif", "r");
			DataSlice in = DataSlice.of(raf);
			TiffImageInput loader = new TiffImageInput(in);
			Vec2l sz = loader.getSize();
			
			int windowSize = 4096;
			//int stepsWide = (int) Math.ceil(sz.x() / (double) windowSize);
			//int stepsHigh = (int) Math.ceil(sz.y() / (double) windowSize);
			//For now, only capture full tiles
			int stepsWide = (int) (sz.x() / windowSize);
			int stepsHigh = (int) (sz.y() / windowSize);
			
			File folder = new File("tiles");
			folder.mkdir();
			
			for(int y=0; y<stepsHigh; y++) {
				for(int x=0; x<stepsWide; x++) {
					long ofsX = x * (long) windowSize;
					long ofsY = y * (long) windowSize;
					ImageData window = loader.loadWindow(ofsX, ofsY, 4096,4096);
					System.out.println("Writing Image...");
					PNGImageWriter.write(window, new FileOutputStream(new File(folder, "out_"+x+"_"+y+".png")));
					System.out.println("Complete.");
				}
			}
			
			System.out.println("All tiles complete.");
			
			//DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("out.raw")));
			//for(int i=0; i<data.length; i++) {
			//	out.writeInt(data[i]);
			//}
			//out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}
}
