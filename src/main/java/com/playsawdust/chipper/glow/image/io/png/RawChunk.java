package com.playsawdust.chipper.glow.image.io.png;

import java.io.DataOutputStream;
import java.io.IOException;

public class RawChunk extends PNGChunk {
	int chunkType;
	byte[] data;
	
	public String getAsciiType() {
		String result = "";
		result += (char) ((chunkType >> 24) & 0xFF);
		result += (char) ((chunkType >> 16) & 0xFF);
		result += (char) ((chunkType >>  8) & 0xFF);
		result += (char) ((chunkType >>  0) & 0xFF);
		return result;
	}

	@Override
	public int getChunkType() {
		return -1;
	}

	@Override
	public void writeChunkData(DataOutputStream out) throws IOException {
		out.write(data);
	}
}