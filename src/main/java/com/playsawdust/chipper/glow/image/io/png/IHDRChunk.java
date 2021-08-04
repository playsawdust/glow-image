package com.playsawdust.chipper.glow.image.io.png;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import com.playsawdust.chipper.glow.image.io.PNGImageLoader;

public class IHDRChunk extends PNGChunk {
	public static final int TYPE_TAG = (byte)'I' << 24 | (byte)'H' << 16 | (byte)'D' << 8 | (byte)'R';
	
	public int width;
	public int height;
	public int bitDepth = 8; //8-bit samples
	public int colorType = PNGImageLoader.COLORTYPE_RGBA;
	public int compression = 0; //deflate
	public int filterMethod = 0; //in this filter method, none, sub, up, average, and paeth are allowed filter-type values
	public int interlaceMethod = 0; //no interlacing
	
	public void read(DataInputStream in) throws IOException {
		CheckedInputStream checked = new CheckedInputStream(in, new CRC32());
		DataInputStream din = new DataInputStream(checked); //This layering seems unnecessary but it's required.
		width = din.readInt();
		height = din.readInt();
		bitDepth = din.read();
		colorType = din.read();
		compression = din.read();
		filterMethod = din.read();
		interlaceMethod = din.read();
		int checksum = in.readInt();
		int actual = (int) checked.getChecksum().getValue();
		
		if (checksum!=actual) System.out.println("Warning: Checksum did not match - file: "+Integer.toHexString(checksum)+" actual: "+Integer.toHexString(actual));
	}
	
	@Override
	public void writeChunkData(DataOutputStream out) throws IOException {
		out.writeInt(width);
		out.writeInt(height);
		out.write((byte) bitDepth);
		out.write((byte) colorType);
		out.write((byte) compression);
		out.write((byte) filterMethod);
		out.write((byte) interlaceMethod);
	}

	@Override
	public int getChunkType() {
		return TYPE_TAG;
	}
}