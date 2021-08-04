package com.playsawdust.chipper.glow.image.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

import com.playsawdust.chipper.glow.image.ImageData;
import com.playsawdust.chipper.glow.image.io.png.IDATChunk;
import com.playsawdust.chipper.glow.image.io.png.IENDChunk;
import com.playsawdust.chipper.glow.image.io.png.IHDRChunk;

public class PNGImageWriter {
	public static void write(ImageData data, OutputStream stream) throws IOException {
		try (DataOutputStream out = new DataOutputStream(stream)) {
			out.writeLong(PNGImageLoader.PNG_MAGIC);
			IHDRChunk header = new IHDRChunk();
			header.width = data.getWidth();
			header.height = data.getHeight();
			header.writeChunk(out);
			
			ByteArrayOutputStream imageData = new ByteArrayOutputStream();
			DeflaterOutputStream deflaterStream = new DeflaterOutputStream(imageData);
			DataOutputStream imageOut = new DataOutputStream(deflaterStream);
			
			for(int y=0; y<data.getHeight(); y++) {
				//Write no-filter filter-type byte
				imageOut.write(0);
				for(int x=0; x<data.getWidth(); x++) {
					int pixel = data.getPixel(x, y);
					imageOut.write((pixel >> 16) & 0xFF); //r
					imageOut.write((pixel >>  8) & 0xFF); //g
					imageOut.write((pixel >>  0) & 0xFF); //b
					imageOut.write((pixel >> 24) & 0xFF); //a
				}
			}
			imageOut.flush();
			deflaterStream.finish();
			deflaterStream.flush();
			imageData.flush();
			
			byte[] toWrite = imageData.toByteArray();
			IDATChunk dataChunk = new IDATChunk();
			dataChunk.data = toWrite;
			dataChunk.writeChunk(out);
			
			IENDChunk endChunk = new IENDChunk();
			endChunk.writeChunk(out);
			out.close();
		}
	}
}
