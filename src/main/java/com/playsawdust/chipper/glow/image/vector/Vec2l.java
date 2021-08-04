package com.playsawdust.chipper.glow.image.vector;

public record Vec2l(long x, long y) {
	
	public Vec2l add(long x, long y) {
		return new Vec2l(this.x + x, this.y + y);
	}
	public Vec2l add(Vec2l other) {
		return add(other.x, other.y);
	}
	
	public Vec2l sub(long x, long y) {
		return new Vec2l(this.x - x, this.y - y);
	}
	
};