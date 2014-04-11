package Managers;

import Sensors.OpticalDistanceSensor;
import lejos.nxt.LightSensor;
import lejos.nxt.SensorPort;

public class SensorManager {

	private OpticalDistanceSensor rightIR = new OpticalDistanceSensor(SensorPort.S3);
	private OpticalDistanceSensor leftIR = new OpticalDistanceSensor(SensorPort.S4);
	private OpticalDistanceSensor middleIR = new OpticalDistanceSensor(SensorPort.S2);
	
	private LightSensor lightSensor = new LightSensor(SensorPort.S1);
	
	// Light values
	private static int innerTrack = 31;
	private static int outerTrack = 23;
	private static int whiteTrack = 47;
	private static int specialZone = 43;
	
	public String getDebugString() {
		return "l: " + leftIR.getDistance() + " m: " + middleIR.getDistance() + " r: " + rightIR.getDistance();
	}
	
	public void enable() {
		lightSensor.setFloodlight(true);
		rightIR.powerOn();
		leftIR.powerOn();
		middleIR.powerOn();
	}
	
	public void disable() {
		lightSensor.setFloodlight(false);
		rightIR.powerOff();
		leftIR.powerOff();
		middleIR.powerOff();
	}
	
	public boolean canSeeObstacle() {
		return canSeeObstacle(leftIR) || canSeeObstacle(rightIR) || canSeeObstacle(middleIR);
	}
	
	public boolean canSeeObstacleInFront() {
		return canSeeObstacle(rightIR) || canSeeObstacle(middleIR);
	}
	
	public boolean canSeeObstacleToSide() {
		return canSeeObstacle(leftIR);
	}
	
	public boolean canSeeObstacleLeft() {
		return canSeeObstacle(leftIR);
	}
	
	public boolean canSeeObstacleRight() {
		return canSeeObstacle(rightIR);
	}
	
	public boolean canSeeObstacleMiddle() {
		return canSeeObstacle(middleIR);
	}
	
	public boolean canSeeEdge() {
		// Approximate. Anything darker than the midrange of a special zone 
		// and the inner line should be one of the track boundaries
		int offset = (innerTrack + specialZone) / 2;
		return lightSensor.getLightValue() < offset;
	}
	
	public boolean canSeeOutsideTrack() {
		return lightSensor.getLightValue() < (outerTrack + innerTrack)/2;
	}
	
	public boolean canSeeWhite() {
		//return lightSensor.getLightValue() > (whiteTrack + specialZone)/2;
		return lightSensor.getLightValue() > specialZone;
	}
	
	public int getLightError(boolean insideTrack) {
		int target = insideTrack ? getOffset(innerTrack, (whiteTrack+specialZone)/2, 0.5) : getOffset(outerTrack, whiteTrack, 0.33);
		return target - lightSensor.getLightValue();
	}
	
	private int getOffset(int track, int inner, double ratio) {
		return (int)(track*ratio + inner*(1-ratio));
	}
	
	private boolean canSeeObstacle(OpticalDistanceSensor sensor) {
		return sensor.getDistance() < 100;
	}
}
