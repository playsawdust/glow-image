package com.playsawdust.chipper.glow.image.io;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

import com.playsawdust.chipper.glow.image.ImageData;
import com.playsawdust.chipper.glow.image.vector.Vec2l;
import com.playsawdust.chipper.glow.io.DataSlice;



public class TiffImageInput implements WindowedImageInput {
	
	private DataSlice in;
	private TiffMeta meta;
	private IFD firstIfd;
	private boolean contiguous = false;
	private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
	
	public TiffImageInput(DataSlice slice) throws IOException {
		this.in = slice; //new CachedDataSlice(slice);
		
		readHeader();
	}
	
	
	/**
	 * The original DOS baseline TIFF decoders would have been unable to process arrays bigger than 64KiB because they'd try to load an entire tag into one segment.
	 * Negative or unreasonably large array sizes might instead point towards corruption in the IFD itself.
	 */
	public static final int REASONABLE_INT_ARRAY_SIZE = 65_534;
	
	
	private void readHeader() throws IOException {
		in.reset();
		
		//boolean bigEndian = false;
		boolean bigTiff = false;
		int bytesPerOffset = 4;
		
		int a = in.read();
		int b = in.read();
		if (a!=b) throw new IOException("Expected: The first two characters of a valid TIFF file are the same, and indicate endianness. These are different.");
		switch(a) {
		case 0x49:
			this.byteOrder = ByteOrder.LITTLE_ENDIAN;
			//bigEndian = false;
			in.setByteOrder(ByteOrder.LITTLE_ENDIAN);
			break;
		case 0x4D:
			this.byteOrder = ByteOrder.BIG_ENDIAN;
			//bigEndian = true;
			in.setByteOrder(ByteOrder.BIG_ENDIAN);
			break;
		default:
			throw new IOException("Unknown endianness identifier: "+(char)a);
		}
		
		int fortyTwo = in.readI16u();
		//int checkFirst = in.read();
		//int checkSecond = in.read();
		//int fortyTwo = align(checkFirst, checkSecond, bigEndian);
		if (fortyTwo==43) {
			bigTiff = true;
		} else if (fortyTwo==42) {
			//Still good, bigTiff can stay false
		} else {
			throw new IOException("Expected: Signature and alignment verification 42 (or bigTIFF 43), found "+fortyTwo+", byteOrder="+byteOrder);
		}
		
		if (bigTiff) {
			bytesPerOffset = in.readI16u();
			if (bytesPerOffset!=8) throw new IOException("Unusual BigTIFF BytesPerOffset: "+bytesPerOffset);
			int padding = in.readI16u();
			if (padding!=0) throw new IOException("Unusual BigTIFF padding value: "+padding);
		}
		
		System.out.println("BytesPerOffset: "+bytesPerOffset);
		
		long firstIFD = readOffset(in, bytesPerOffset, byteOrder==ByteOrder.BIG_ENDIAN);
		//int firstIFD = align(in.read(), in.read(), in.read(), in.read(), bigEndian);
		System.out.println("Found " + byteOrder + " encoding with IFD starting at "+firstIFD+" bytes.");
		in.seek(firstIFD);
		
		//Read first IFD
		firstIfd = new IFD(in, bytesPerOffset, byteOrder==ByteOrder.BIG_ENDIAN);
		
		System.out.println("IFD Entries: "+firstIfd.entries.size());
		
		meta = new TiffMeta();
		for(IFDEntry entry : firstIfd.entries) {
			meta.apply(entry, in, byteOrder==ByteOrder.BIG_ENDIAN);
		}
		System.out.println(firstIfd.toString());
		System.out.println("Parsed Metadata: ");
		System.out.println(meta);
		
		//figure out to what extent the strips are contiguous
		long contiguousStart = meta.stripOffsets[0];
		long contiguousLength = meta.stripByteCounts[0];
		for(int i=1; i<meta.stripOffsets.length; i++) {
			long predictedNextStrip = contiguousStart+contiguousLength;
			if (meta.stripOffsets[i] == predictedNextStrip) {
				contiguousLength += meta.stripByteCounts[i];
			} else {
				System.out.println("Strip "+i+" is non-contiguous (start location of "+meta.stripOffsets[i]+" instead of "+predictedNextStrip);
				break;
			}
		}
		System.out.println("Contiguous window is from "+contiguousStart+" for "+contiguousLength+" bytes.");
		int bytesPerPixel = (meta.rBits + meta.gBits + meta.bBits + meta.aBits) / 8;
		long predictedImageBytes = meta.width * meta.height * bytesPerPixel;
		if (predictedImageBytes == contiguousLength) {
			System.out.println("Accessing in fast contiguous mode");
			contiguous = true;
		} else {
			System.out.println("Predicted: "+predictedImageBytes);
			System.out.println("Accessing in slow mode");
		}
		
		//Read in a window
		/*
		ImageData data = new ImageData((int) meta.width, (int) meta.height);
		int safeStrips = Math.min(meta.stripByteCounts.length, meta.stripOffsets.length);
		int imageOffset = 0;
		int bytesPerPixel = (meta.rBits + meta.gBits + meta.bBits + meta.aBits) / 8;
		System.out.println("Reading strips...");
		for(int i=0; i<safeStrips; i++) {
			in.seek(meta.stripOffsets[i]);
			DataSlice strip = in.slice(meta.stripByteCounts[i]);
			
			//byte[] strip = new byte[(int) meta.stripByteCounts[i]];
			//int bytesRead = in.read(strip);
			//System.out.println("Strip: "+Arrays.toString(strip));
			//TODO: Figure out number of bytes per pixel; for now, assume RGB color.
			for(int j=0; j<meta.stripByteCounts[i]/bytesPerPixel; j++) {
				
				
				int ofs = j*bytesPerPixel;
				int br = 0;
				if (meta.rBits>0) br = scaleSample(strip, ofs, meta.rBits);
				//int br = strip[ofs] & 0xFF;
				//int bg = strip[ofs+1] & 0xFF;
				//int bb = strip[ofs+2] & 0xFF;
				int bg = br;
				int bb = br;
				int col = (0xFF << 24) | (br << 16) | (bg << 8) | (bb << 0);
				data.data()[imageOffset] = col;
				imageOffset++;
			}
			System.out.print("#");
			if (i%80==79) System.out.println();
		}
		System.out.println();
		System.out.println("Strips finished.");
		
		return data;
		*/
	}
	
	@Override
	public Vec2l getSize() {
		return new Vec2l(meta.width, meta.height);
	}

	@Override
	public ImageData loadWindow(long x, long y, int width, int height) throws IOException {
		ImageData data = new ImageData((int) width, (int) height);
		
		int bytesPerPixel = (meta.rBits + meta.gBits + meta.bBits + meta.aBits) / 8;
		
		byte[] pixelBuffer = new byte[bytesPerPixel];
		
		if (contiguous) {
			for(int yi=0; yi<height; yi++) {
				
				//TODO: Prevent seeking outside the source image
				long lineStart = meta.stripOffsets[0] + ((y+yi) * meta.width * bytesPerPixel) + (x * bytesPerPixel);
				in.seek(lineStart);
				
				//System.out.println("Line "+yi+" at offset "+lineStart);
				
				for(int xi=0; xi<width; xi++) {
					in.copy(pixelBuffer);
					//System.out.println(Integer.toHexString(pixelBuffer[0]&0xFF)+" "+Integer.toHexString(pixelBuffer[1]&0xFF));
					data.setPixel(xi, yi, getArgb(pixelBuffer));
				}
			}
			
			System.out.println("Complete.");
		} else {
			throw new IOException();
		}
		
		
		
		/*
		
		
		int safeStrips = Math.min(meta.stripByteCounts.length, meta.stripOffsets.length);
		//int imageOffset = 0;
		int bytesPerPixel = (meta.rBits + meta.gBits + meta.bBits + meta.aBits) / 8;
		System.out.println("Reading strips... (x"+safeStrips+")");
		
		long xPos = 0;
		long yPos = 0;
		
		for(int i=0; i<safeStrips; i++) {
			//System.out.println("Strip #"+i);
			//in.seek(meta.stripOffsets[i]);
			//long pixels = meta.stripByteCounts[i];
			//long potentialLines = (pixels / meta.width) + 1;
			//if (yPos+potentialLines<y || yPos>y+height) {
			//	xPos += pixels;
			//	while(xPos>=meta.width) {
			//		xPos -= meta.width;
			//		yPos++;
			//	}
			//	continue;
			//}
			
			DataSlice strip = in.copy(meta.stripOffsets[i], (int)meta.stripByteCounts[i]);
			//DataSlice strip = in.slice(meta.stripByteCounts[i]);
			
			//byte[] strip = new byte[(int) meta.stripByteCounts[i]];
			//int bytesRead = in.read(strip);
			//System.out.println("Strip: "+Arrays.toString(strip));
			//TODO: Figure out number of bytes per pixel; for now, assume RGB color.
			for(int j=0; j<meta.stripByteCounts[i]/bytesPerPixel; j++) {
				int ofs = j*bytesPerPixel;
				int br = 0;
				if (meta.rBits>0) br = scaleSample(strip, ofs, meta.rBits);
				//int br = strip[ofs] & 0xFF;
				//int bg = strip[ofs+1] & 0xFF;
				//int bb = strip[ofs+2] & 0xFF;
				int bg = br;
				int bb = br;
				int col = (0xFF << 24) | (br << 16) | (bg << 8) | (bb << 0);
				
				long dataX = (xPos - x);
				long dataY = (yPos - y);
				
				data.setPixel((int) dataX, (int) dataY, col);
				
				//data.data()[imageOffset] = col;
				//imageOffset++;
				
				xPos += 1L;
				if (xPos>=meta.width) {
					xPos = 0;
					yPos += 1L;
				}
			}
			
			if (i%80==79) {
				System.out.print(" #"+i);
				//System.out.println();
			}
		}
		System.out.println();
		System.out.println("Strips finished.");
		*/
		return data;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
	
	/*
	public static int scaleSample(DataSlice strip, int offset, int bits) throws IOException {
		if (bits==16) return strip.read(offset+1); //high bits because of *course* the samples are nearly always hard-coded LE
		if (bits==8) return strip.read(offset);
		return strip.read(offset) & (int)(Math.pow(2, bits)-1); //try and probably fail to mask out arbitrary sub-8 bits
	}*/
	
	public int getArgb(byte[] pixel) {
		if (meta.rBits==16 && meta.gBits==0 && meta.bBits==0) {
			//16-bit grays
			
			int hi = pixel[1] & 0xFF;
			int lo = pixel[0] & 0xFF;
			
			if (this.byteOrder==ByteOrder.BIG_ENDIAN) {
				hi = pixel[0] & 0xFF;
				lo = pixel[1] & 0xFF;
			}
			
			int col = (hi << 8) | lo;
			
			if (meta.sampleFormats[0]==2) {
				//signed! We'll have to rescale the samples so they make sense!
				col += 32768;
			}
			
			//Now rescale to 8 bits. I promise all the 16-bit nonsense was necessary.
			col = col >> 8;
			
			return 0xFF_000000 | (col << 16) | (col << 8) | col;
		} else {
			
			return 0xFF_FF0000;
		}
	}
	
	/**
	 * Unpacks a color from an arbitrary pixel encoding in bytes. Suitable for most images.
	 */
	public static long getDeepColor(byte[] pixel, int aBytes, int rBytes, int gBytes, int bBytes, ByteOrder order) {
		long result = 0L;
		int pos = 0;
		if (aBytes==0) {
			//No alpha; make every color opaque
			result |= 0xFFFF_0000_0000_0000L;
		} else {
			//for(int i=0; i<)
		}
		
		
		
		return result;
	}
	
	/** Unpacks a sample from an arbitrary pixel encoding given its position and size in bytes. Suitable for most images. */
	public static long getDeepSample(byte[] pixel, int start, int count, ByteOrder order) {
		long result = 0L;
		
		for(int i=0; i<count; i++) {
			if (order==ByteOrder.BIG_ENDIAN) {
				if (start+i >= pixel.length) break;
				result = (result << 8) | (pixel[start+i] & 0xFF);
			} else {
				
			}
		}
		
		return result;
	}
	
	/*
	public static int align(int a, int b, boolean be) {
		if (be) {
			return ((a & 0xFF) << 8) | (b & 0xFF);
		} else {
			return ((b & 0xFF) << 8) | (a & 0xFF);
		}
	}
	
	public static int align(int a, int b, int c, int d, boolean be) {
		if (be) {
			return  ((a & 0xFF) << 24) |
					((b & 0xFF) << 16) |
					((c & 0xFF) <<  8) |
					 (d & 0xFF);
		} else {
			return  ((d & 0xFF) << 24) |
					((c & 0xFF) << 16) |
					((b & 0xFF) <<  8) |
					 (a & 0xFF);
		}
	}
	
	public static int align(int a, int b, int c, int d, int e, int f, int g, int h, boolean be) {
		if (be) {
			return  ((a & 0xFF) << 56) |
					((b & 0xFF) << 48) |
					((c & 0xFF) << 40) |
					((d & 0xFF) << 32) |
					((e & 0xFF) << 24) |
					((f & 0xFF) << 16) |
					((g & 0xFF) <<  8) |
					 (h & 0xFF);
		} else {
			return
					((h & 0xFF) << 56) |
					((g & 0xFF) << 48) |
					((f & 0xFF) << 40) |
					((e & 0xFF) << 32) |
					((d & 0xFF) << 24) |
					((c & 0xFF) << 16) |
					((b & 0xFF) <<  8) |
					 (a & 0xFF);
		}
	}*/
	
	public static long readOffset(DataSlice in, int offsetSize, boolean bigEndian) throws IOException {
		if (bigEndian) {
			long result = 0L;
			for(int i=0; i<offsetSize; i++) {
				result = result << 8;
				result |= (in.read() & 0xFF);
			}
			return result;
		} else {
			long result = 0L;
			long pos = 0L;
			for(int i=0; i<offsetSize; i++) {
				long shiftIn = in.read() & 0xFF;
				//System.out.println("0x"+Integer.toHexString((int) shiftIn));
				shiftIn = shiftIn << pos;
				//System.out.println("  << "+pos+" == 0x"+Integer.toHexString((int) shiftIn));
				result |= shiftIn;
				//System.out.println("  Result: 0x"+Long.toHexString(result));
				pos += 8;
			}
			//System.out.println("Final Result: 0x"+Long.toHexString(result));
			return result;
		}
	}
	
	//public static int b2i(byte b) {
	//	return ((int)b) & 0xFF;
	//}
	
	//public static int b2i(int i) {
	//	return i & 0xFF;
	//}
	
	public static class IFD {
		//private static byte[] buffer = new byte[20];
		
		private ArrayList<IFDEntry> entries = new ArrayList<>();
		private long nextIFD;
		
		public IFD(DataSlice in, int bytesPerOffset, boolean bigEndian) throws IOException {
			//numEntries has some special semantics
			int numEntrySize = (bytesPerOffset==4) ? 2 : 8;
			long numEntries = readOffset(in, numEntrySize, bigEndian);
			
			for(long i=0; i<numEntries; i++) {
				
				int tag = in.readI16u();
				int type = in.readI16u();
				long count = readOffset(in, bytesPerOffset, bigEndian);
				long offset = readOffset(in, bytesPerOffset, bigEndian);
				
				entries.add(new IFDEntry(tag, type, count, offset));
			}
		}
		
		/*@Override
		public String toString() {
			return "{"+
					"entries:"+entries.toString() +
					", next:0x"+Describe.hex(nextIFD) +
					"}";
		}*/
	}
	
	public static class IFDEntry {
		public int tag;
		public int type;
		public long count;
		public long offset;
		
		public IFDEntry(int tag, int type, long count, long offset) {
			this.tag = tag;
			this.type = type;
			this.count = count;
			this.offset = offset;
		}
		
		@Override
		public String toString() {
			StringBuilder result = new StringBuilder("{");
			
			result.append("tag:");
			Tag tagObj = Tag.valueOf(tag);
			if (tagObj!=null) {
				result.append(Tag.valueOf(tag).name());
			} else {
				result.append("0x");
				//result.append(Describe.hexShort(tag));
			}
			
			result.append(", type:");
			result.append(EntryType.valueOf(type));
			
			
			result.append(", count:");
			result.append(count);
			
			result.append(", offset:0x");
			//result.append(Describe.hex(offset));
			
			result.append('}');
			
			return result.toString();
		}
	}
	
	public enum EntryType {
		INVALID, //Invalid entry placed at the front because valid TypeDesc entries start at 1
		I8,
		ASCII,
		I16,
		I32,
		RATIONAL,
		I8S,
		UNDEFINED,
		I16S,
		I32S,
		SIGNED_RATIONAL,
		F32,
		F64,
		Q1,
		Q2,
		Q3,
		I64,
		I64S,
		IFD8,
		;
		
		public static EntryType valueOf(int i) {
			if (i<=0 || i>=values().length) return INVALID;
			return values()[i];
		}
	}
	
	private enum Tag {
		IMAGE_WIDTH      (0x0100),
		IMAGE_HEIGHT     (0x0101),
		BITS_PER_SAMPLE  (0x0102),
		COMPRESSION      (0x0103),
		PHOTOMETRIC_INTERPRETATION (0x0106),
		FILL_ORDER       (0x010A),
		FILENAME         (0x010D),
		DESCRIPTION      (0x010E),
		STRIP_OFFSETS    (0x0111),
		SAMPLES_PER_PIXEL(0x0115),
		STRIP_BYTE_COUNTS(0x0117),
		ROWS_PER_STRIP   (0x0116),
		PLANAR_CONFIG    (0x011C),
		
		RESOLUTION_X     (0x011A),
		RESOLUTION_Y     (0x011B),
		RESOLUTION_UNIT  (0x0128),
		
		SAMPLE_FORMAT    (0x0153),
		
		MODEL_TIEPOINT   (0x8482),
		;
		public final int id;
		
		
		Tag(int id) {
			this.id = id;
		}
		
		public static Tag valueOf(int i) {
			for(Tag t : values()) {
				if (t.id==i) return t;
			}
			return null;
		}
	}
	
	private class TiffMeta {
		public long width = 0;
		public long height = 0;
		public int rBits = 8;
		public int gBits = 8;
		public int bBits = 8;
		public int aBits = 0;
		public String compression = "None";
		public String photometricInterpretation = "BlackIsZero";
		public String name = "";
		public String desc = "";
		boolean isSeparatePlanes = false;
		
		double[] modelTiepoint = new double[0];
		public long[] sampleFormats = new long[0];
		
		public long[] stripOffsets;
		public long[] stripByteCounts;
		
		@Override
		public String toString() {
			return  "{"+
					  "width:"+width+
					", height:"+height+
					", bitsPerSample:"+rBits+"/"+gBits+"/"+bBits+"/"+aBits+
					", sampleFormats:"+Arrays.toString(sampleFormats)+
					", compression:"+compression+
					", photometricInterpretation:"+photometricInterpretation+
					", name:"+name+
					", desc:"+desc+
					", modelTiepoint: "+Arrays.toString(modelTiepoint)+
					", isSeparatePlanes: "+isSeparatePlanes+
					"}";
		}
		
		private long number(IFDEntry entry, DataSlice file, boolean be) throws IOException {
			EntryType type = EntryType.valueOf(entry.type);
			if (entry.count!=1) throw new IOException("Expected single value, got a vector of "+entry.count+" values.");
				
			switch(type) {
			case I8:
				return entry.offset;
			case I16:
				return entry.offset;
			case I32:
				return entry.offset;
			case RATIONAL:
			case SIGNED_RATIONAL:
				file.seek(entry.offset);
				int numerator = file.readI32s();
				int denominator = file.readI32s();
				//int numerator = align(file.read(), file.read(), file.read(), file.read(), be);
				//int denominator = align(file.read(), file.read(), file.read(), file.read(), be);
				return numerator/denominator; //Get as close as we can.
			case I8S:
				return entry.offset;
			case I16S:
				return entry.offset;
			case I32S:
				return entry.offset;
			case I64:
				return entry.offset;
			case I64S:
				return entry.offset;
			case IFD8:
				return entry.offset;
			default:
				throw new IOException("Don't know how to translate "+type.name()+" into an integer type.");
			}
		}
		
		private long[] numberArray(IFDEntry entry, DataSlice file, boolean be) throws IOException {
			EntryType type = EntryType.valueOf(entry.type);
			if (entry.count==0) return new long[0];
			if (entry.count<0 || entry.count>REASONABLE_INT_ARRAY_SIZE) throw new IOException("Unusual array size ("+entry.count+") suggests corruption in the IFD");
			
			long[] result = new long[(int) entry.count];
			file.seek(entry.offset);
			//System.out.println(type);
			switch(type) {
			case I16:
				if (entry.count<2) {
					result[0] = entry.offset;
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readI16u();
				}
				return result;
			case I32:
				if (entry.count<2) {
					result[0] = entry.offset;
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readI32s();
				}
				return result;
			case I64:
				if (entry.count<2) {
					result[0] = entry.offset;
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readI64s();
				}
				return result;
				
			default:
				throw new IOException("Don't know how to translate "+type.name()+" into an integer array type.");
			}
		}
		
		private double[] floatArray(IFDEntry entry, DataSlice file, boolean be) throws IOException {
			EntryType type = EntryType.valueOf(entry.type);
			if (entry.count==0) return new double[0];
			if (entry.count<0 || entry.count>REASONABLE_INT_ARRAY_SIZE) throw new IOException("Unusual array size ("+entry.count+") suggests corruption in the IFD");
			
			double[] result = new double[(int) entry.count];
			file.seek(entry.offset);
			
			switch(type) {
			case I16:
				if (entry.count<2) {
					result[0] = entry.offset;
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readI16u();
				}
				return result;
			case I32:
				if (entry.count<2) {
					result[0] = entry.offset;
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readI32s();
				}
				return result;
			case I64:
				if (entry.count<2) {
					result[0] = entry.offset;
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readI64s();
				}
				return result;
			case F32:
				if (entry.count<2) {
					result[0] = Float.intBitsToFloat((int) entry.offset);
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readF32s();
				}
				return result;
			case F64:
				if (entry.count<2) {
					result[0] = Double.longBitsToDouble(entry.offset);
					return result;
				}
				for(int i=0; i<entry.count; i++) {
					result[i] = file.readF64s();
				}
				return result;
			default:
				throw new IOException("Don't know how to translate "+type.name()+" into an integer array type.");
			}
		}
		
		
		private String string(IFDEntry entry, DataSlice file) throws IOException {
			if (entry.count<=1) return ""; //count includes the trailing null
			
			file.seek(entry.offset);
			byte[] buf = new byte[(int) (entry.count-1)];
			for(int i=0; i<buf.length; i++) {
				buf[i] = (byte) file.read();
			}
			return new String(buf, Charset.forName("US-ASCII"));
		}
		
		public void apply(IFDEntry entry, DataSlice file, boolean be) throws IOException {
			if (Tag.valueOf(entry.tag)==null) return; //We don't know this tag, so skip it.
			
			switch(Tag.valueOf(entry.tag)) {
			
			case IMAGE_WIDTH:  width  = number(entry, file, be); break;
			case IMAGE_HEIGHT: height = number(entry, file, be); break;
			case BITS_PER_SAMPLE:
				long[] bitsPerSample = numberArray(entry, file, be);
				if (bitsPerSample.length>0) rBits = (int) bitsPerSample[0]; else rBits = 0;
				if (bitsPerSample.length>1) gBits = (int) bitsPerSample[1]; else gBits = 0;
				if (bitsPerSample.length>2) bBits = (int) bitsPerSample[2]; else bBits = 0;
				if (bitsPerSample.length>3) aBits = (int) bitsPerSample[3]; else aBits = 0;
				break;
			case COMPRESSION:
				int comp = (int) number(entry, file, be);
				switch(comp) {
				case 1: compression = "None"; break;
				case 2: compression = "ModifiedHuffmanRLE"; break;
				case 32773: compression = "PackBitsRLE"; break;
				default: compression = "Unknown"; break;
				}
				break;
			case PHOTOMETRIC_INTERPRETATION:
				if (entry.count==1 && EntryType.valueOf(entry.type)==EntryType.I16) {
					if (entry.offset==0) photometricInterpretation = "WhiteIsZero";
					else if (entry.offset==1) photometricInterpretation = "BlackIsZero";
					else if (entry.offset==2) photometricInterpretation = "RGBColor";
					else if (entry.offset==3) photometricInterpretation = "PalettedColor";
					else if (entry.offset==4) photometricInterpretation = "TransparencyMask";
				}
				break;
				
			case FILL_ORDER:
				if (entry.count==1 && EntryType.valueOf(entry.type)==EntryType.I16) {
					if (entry.offset==2) System.out.println("HIGHLY UNUSUAL fill order! This image will probably unpack with weird pixels.");
					else if (entry.offset!=1) System.out.println("Corrupted or nonstandard fill order. This order doesn't exist in the TIFF standard at *all*.");
				}
				break;
				
			case FILENAME:
				if (entry.count>0 && EntryType.valueOf(entry.type)==EntryType.ASCII) {
					name = string(entry, file);
				}
				break;
			case DESCRIPTION:
				if (entry.count>0 && EntryType.valueOf(entry.type)==EntryType.ASCII) {
					desc = string(entry, file);
				}
				break;
				
			case RESOLUTION_X:
				break;
			case RESOLUTION_Y:
				break;
				
			case RESOLUTION_UNIT:
				break;
			
			case STRIP_OFFSETS:
				stripOffsets = numberArray(entry,file,be);
				System.out.println("Strip offsets: [");
				//for(int i=0; i<stripOffsets.length; i++) {
				//	System.out.print(Long.toHexString(stripOffsets[i])+", ");
				//	if ((i%30) == 0) System.out.println();
				//}
				System.out.println("]");
				//System.out.println("Retrieved strip offsets: "+Arrays.toString(stripOffsets));
				break;
			case STRIP_BYTE_COUNTS:
				stripByteCounts = numberArray(entry,file,be);
				//System.out.println("Retrieved strip byte counts: "+Arrays.toString(stripByteCounts));
				break;
			case ROWS_PER_STRIP:
				break;
			case PLANAR_CONFIG:
				this.isSeparatePlanes = (entry.offset==2);
				break;
			case SAMPLE_FORMAT:
				sampleFormats = numberArray(entry, file, be);
				break;
			case SAMPLES_PER_PIXEL:
				break;
			case MODEL_TIEPOINT:
				modelTiepoint = floatArray(entry, file, be);
				break;
			default:
				break;
			}
		}
	}
}