package Managers;

import lejos.nxt.MotorPort;

public class DeadReckoningManager {
	public static final double NinetyDegrees = Math.PI/2;
	
	public double x;
	public double y;
	public double heading;
	
	private MotorPort leftMotor;
	private MotorPort rightMotor;
	
	private int lastLeftReading;
	private int lastRightReading;
	
	private final int wheelDiameter = 54;
	private final int jockeyDiameter = 119;
	
	private final double distancePerTick = 2 * Math.PI * (wheelDiameter / 2.0) / 360;
	private final double ticksPerRotation = 2 * Math.PI * (jockeyDiameter / 2.0) / distancePerTick;
	private final double radiansPerTick = 2 * Math.PI / ticksPerRotation;

	public DeadReckoningManager(MotorPort left, MotorPort right) {
		reset();
		
		leftMotor = left;
		rightMotor = right;
		
		lastLeftReading = left.getTachoCount();
		lastRightReading = right.getTachoCount();
	}
	
	public void reset()
	{
		x = 0;
		y = 0;
		heading = 0;
	}
	
	public void updatePosition() {
		int leftTacho = leftMotor.getTachoCount();
		int rightTacho = rightMotor.getTachoCount();
		
		int leftDiff = leftTacho - lastLeftReading;
		int rightDiff = rightTacho - lastRightReading;
		
		double dDistance = averageTach(leftDiff, rightDiff) * distancePerTick;
		double dHeading = tachDiff(leftDiff, rightDiff) * radiansPerTick;
		
		heading -= dHeading;

		x += dDistance * Math.cos(heading);
		y += dDistance * Math.sin(heading);
		
		lastLeftReading = leftTacho;
		lastRightReading = rightTacho;
	}

	private double averageTach(int left, int right) {
		return ((left) + (right)) / 2.0;
	}

	private double tachDiff(int left, int right) {
		return (right - left) / 2.0;
	}
}
