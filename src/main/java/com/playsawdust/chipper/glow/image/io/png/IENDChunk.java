package com.playsawdust.chipper.glow.image.io.png;

import java.io.DataOutputStream;
import java.io.IOException;

public class IENDChunk extends PNGChunk {
	public static final int TYPE_TAG = (byte)'I' << 24 | (byte)'E' << 16 | (byte)'N' << 8 | (byte)'D';

	@Override
	public int getChunkType() {
		return TYPE_TAG;
	}

	@Override
	public void writeChunkData(DataOutputStream out) throws IOException {
	}
}