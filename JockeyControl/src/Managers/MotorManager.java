package Managers;

import lejos.nxt.BasicMotorPort;
import lejos.nxt.MotorPort;

public class MotorManager {
	private MotorPort left;
	private MotorPort right;
	
	private static MotorManager instance = new MotorManager();
	
	public static void setMotors(MotorPort left, MotorPort right) {
		instance.left = left;
		instance.right = right;
	}
	
	// Stahps.
	public static void stahp() {
		instance.setPower(100, 100, BasicMotorPort.STOP, BasicMotorPort.STOP);
	}
	
	public static void forward(int l, int r) {
		instance.setPower(l, r, BasicMotorPort.FORWARD, BasicMotorPort.FORWARD);
	}
	
	public static void backward(int l, int r) {
		instance.setPower(l, r, BasicMotorPort.BACKWARD, BasicMotorPort.BACKWARD);
	}
	
	public static void turnRight(int power) {
		instance.setPower(power, power, BasicMotorPort.FORWARD, BasicMotorPort.BACKWARD);
	}
	
	public static void turnLeft(int power) {
		instance.setPower(power, power, BasicMotorPort.BACKWARD, BasicMotorPort.FORWARD);
	}
	
	private void setPower(int l, int r, int ldir, int rdir) {
		left.controlMotor(l, ldir);
		right.controlMotor(r, rdir);
	}
}
