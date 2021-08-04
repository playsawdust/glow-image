/*
 * Glow - GL Object Wrapper
 * Copyright (C) 2020 the Chipper developers
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.playsawdust.chipper.glow.image.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import com.playsawdust.chipper.glow.image.ImageData;
import com.playsawdust.chipper.glow.image.io.png.IDATChunk;
import com.playsawdust.chipper.glow.image.io.png.IENDChunk;
import com.playsawdust.chipper.glow.image.io.png.IHDRChunk;
import com.playsawdust.chipper.glow.image.io.png.PNGChunk;
import com.playsawdust.chipper.glow.image.io.png.RawChunk;

public class PNGImageLoader {
	/** The first two bytes of a file or stream can be used to uniquely identify it as a PNG file. Specifically, these two bytes. */
	public static final int PNG_SHORTMAGIC = 0x8950;
	/** {@code 0x89 'P' 'N' 'G' '\r' '\n' CTRL-Z '\n'}, carefully chosen to make your text editor think twice before opening, and do 7-bit integrity checks, right from the first dword */
	public static final long PNG_MAGIC = 0x89504e470d0a1a0aL;
	
	public static final int COLORTYPE_RGB  = 2;
	public static final int COLORTYPE_RGBA = 6;
	
	public static ImageData load(InputStream in) throws IOException {
		try(in) {
			DataInputStream data = new DataInputStream(in);
			
			long magic = data.readLong();
			if (magic!=PNG_MAGIC) throw new IOException("Not a valid PNG file");
			
			IHDRChunk ihdr = null;
			ImageData result = null;
			ByteArrayOutputStream imageDataStream = new ByteArrayOutputStream();
			while(true) {
				try {
					PNGChunk chunk = PNGChunk.readChunk(data);
					if (chunk instanceof IHDRChunk) {
						ihdr = (IHDRChunk) chunk;
						result = new ImageData(ihdr.width, ihdr.height);
						//System.out.println("Size: "+ihdr.width+"x"+ihdr.height);
						if (ihdr.compression!=0) throw new IOException("Unknown compression method");
						if (ihdr.filterMethod!=0) throw new IOException("Image uses unknown extended filter types"); //0 = none/sub/up/average/paeth are allowed filter bytes
						if (ihdr.colorType!=COLORTYPE_RGB && ihdr.colorType!=COLORTYPE_RGBA) throw new IOException("Image uses non-RGB/RGBA colorType");
						if (ihdr.bitDepth!=8) throw new IOException("Only 8-bit samples supported; image uses "+ihdr.bitDepth);
						if (ihdr.interlaceMethod!=0) throw new IOException("Interlaced images are not supported.");
					} else if (chunk instanceof IDATChunk) {
						imageDataStream.write(((IDATChunk) chunk).data);
					} else if (chunk instanceof IENDChunk) {
						//It is safe to interpret the data chunks.
						byte[] imageDataCompressed = imageDataStream.toByteArray();
						InflaterInputStream uncompress = new InflaterInputStream(new ByteArrayInputStream(imageDataCompressed));
						for(int y=0; y<ihdr.height; y++) {
							int filterByte = uncompress.read();
							int priorR = 0x00;
							int priorG = 0x00;
							int priorB = 0x00;
							int priorA = 0x00;
							for(int x=0; x<ihdr.width; x++) {
								int r = uncompress.read();
								int g = uncompress.read();
								int b = uncompress.read();
								int a = 0xFF;
								if (ihdr.colorType==COLORTYPE_RGBA) {
									a = uncompress.read();
								}
								
								if (filterByte==0) {
									//do nothing
								} else if (filterByte==1) { //SUB
									r = ((byte)r + (byte)priorR) & 0xFF;
									g = ((byte)g + (byte)priorG) & 0xFF;
									b = ((byte)b + (byte)priorB) & 0xFF;
									a = ((byte)a + (byte)priorA) & 0xFF;
									
									priorR = r;
									priorG = g;
									priorB = b;
									priorA = a;
								} else if (filterByte==2) { //UP
									int upRGBA = result.getPixel(x,y-1);
									byte upA = (byte)(upRGBA >> 24);
									byte upR = (byte)(upRGBA >> 16);
									byte upG = (byte)(upRGBA >>  8);
									byte upB = (byte)(upRGBA);
									
									r = ((byte)r + upR) & 0xFF;
									g = ((byte)g + upG) & 0xFF;
									b = ((byte)b + upB) & 0xFF;
									a = ((byte)a + upA) & 0xFF;
								} else if (filterByte==3) { //AVERAGE
									int upRGBA = result.getPixel(x,y-1);
									int upA = (upRGBA >> 24) & 0xFF;
									int upR = (upRGBA >> 16) & 0xFF;
									int upG = (upRGBA >>  8) & 0xFF;
									int upB = (upRGBA) & 0xFF;
									
									upA = (upA+priorA)/2;
									upR = (upR+priorR)/2;
									upG = (upG+priorG)/2;
									upB = (upB+priorB)/2;
									
									r = ((byte)r + (byte)upR) & 0xFF;
									g = ((byte)g + (byte)upG) & 0xFF;
									b = ((byte)b + (byte)upB) & 0xFF;
									a = ((byte)a + (byte)upA) & 0xFF;
									
									priorR = r;
									priorG = g;
									priorB = b;
									priorA = a;
								} else if (filterByte==4) { //PAETH
									int upRGBA = result.getPixel(x,y-1);
									int upA = (upRGBA >> 24) & 0xFF;
									int upR = (upRGBA >> 16) & 0xFF;
									int upG = (upRGBA >>  8) & 0xFF;
									int upB = (upRGBA) & 0xFF;
									
									int upLeftRGBA = result.getPixel(x-1, y-1);
									int upLeftA = (upLeftRGBA >> 24) & 0xFF;
									int upLeftR = (upLeftRGBA >> 16) & 0xFF;
									int upLeftG = (upLeftRGBA >>  8) & 0xFF;
									int upLeftB = (upLeftRGBA) & 0xFF;
									
									if (x>0) {
										r = ((byte)r + (byte)paeth(priorR, upR, upLeftR)) & 0xFF;
										g = ((byte)g + (byte)paeth(priorG, upG, upLeftG)) & 0xFF;
										b = ((byte)b + (byte)paeth(priorB, upB, upLeftB)) & 0xFF;
										a = ((byte)a + (byte)paeth(priorA, upA, upLeftA)) & 0xFF;
									} else {
										r = ((byte)r + upR) & 0xFF;
										g = ((byte)g + upG) & 0xFF;
										b = ((byte)b + upB) & 0xFF;
										a = ((byte)a + upA) & 0xFF;
									}
									
									priorR = r;
									priorG = g;
									priorB = b;
									priorA = a;
								}
								
								
								
								result.setPixel(x, y, a<<24 | r<<16 | g<<8 | b);
								//result.setPixel(x, y, filterByte<<7);
							}
						}
						return result;
					} else if (chunk instanceof RawChunk) {
						System.out.println("Unknown chunk of type "+((RawChunk) chunk).getAsciiType());
					}
				} catch (EOFException ex) {
					if (result==null) throw new IOException("Image file did not contain an IHDR chunk!");
					return result;
				} finally {
					data.close(); // Technically unnecessary but suppresses some warnings
				}
			}
		} finally {
			in.close();
		}
	}
	
	private static int paeth(int left, int up, int upLeft) {
		//Distance to a/b/c
		int pLeft   = Math.abs(up-upLeft);
		int pUp     = Math.abs(left-upLeft);
		int pUpLeft = Math.abs(left + up - upLeft - upLeft);
		
		//Return the smallest of the distances. Order of tie-breaking is important!!!
		if (pLeft<=pUp && pLeft<=pUpLeft) {
			return left;
		}
		if (pUp<=pUpLeft) {
			return up;
		}
		return upLeft;
	}
}
