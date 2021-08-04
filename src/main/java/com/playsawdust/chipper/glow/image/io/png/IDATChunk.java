package com.playsawdust.chipper.glow.image.io.png;

import java.io.DataOutputStream;
import java.io.IOException;

public class IDATChunk extends PNGChunk {
	public static final int TYPE_TAG = (byte)'I' << 24 | (byte)'D' << 16 | (byte)'A' << 8 | (byte)'T';
	public byte[] data;
	
	@Override
	public int getChunkType() {
		return TYPE_TAG;
	}
	
	@Override
	public void writeChunkData(DataOutputStream out) throws IOException {
		out.write(data);
	}
}