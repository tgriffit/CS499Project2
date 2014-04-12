import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import Managers.*;
import lejos.nxt.*;
import lejos.util.Delay;


public class JockeyRace extends JFrame {

	private static final long serialVersionUID = 1L;

	private enum Mode {
		Stop, Wait, Map, Race
	}

	private static Mode mode = Mode.Wait;

	static MotorPort leftMotor = MotorPort.C;
	static MotorPort rightMotor = MotorPort.A;

	public static JockeyRace NXTrc;

	public static JLabel modeLbl;
	public static JLabel commands;
	public static ButtonHandler bh = new ButtonHandler();
	
	private static JTextField kpEntry, kiEntry, kdEntry, speedmultEntry, targetpowerEntry, errorEntry;
	private static JButton commitButton;
	
	private static int kp = 100;
	private static int ki = 10;
	private static int kd = 50;
	
	private static int targetpower = 5;
	
	private static int speedmult = 12;
	private static double currentmult = 1.0;
	
	private static int allowedError = 30;
	
	private static int integral = 0;
	private static int sqrIntegral = 0;
	private static int oldError = 0;

	private static DeadReckoningManager position;
	private static SensorManager sensors;
	
	private static boolean foundLine = false;
	private static boolean sawObstacle = false;
	private static boolean skipBadPart = false;
	private static boolean obstaclesFound = false;
	private static boolean mappingDone = false;
	private static boolean ignoreRightSensor = false;
	
	// Switches jockey to follow the inside track instead of the outside one.
	private static boolean useInsideTrack = false;
	
	public JockeyRace() {
		setLayout(new FlowLayout());

		setTitle("Jockey Self-Control");
		setBounds(400, 350, 300, 200);
		addMouseListener(bh);
		addKeyListener(bh);

		String cmds = "<html>Buttons:<br>" 
				+ "m: Map Track (Unimplemented)<br>"
				+ "r: Race!<br>"
				+ "<br>"
				+ "s: Stop<br>" 
				+ "<br>" 
				+ "q: Quit</html>";

		commands = new JLabel(cmds);
		add(commands);

		Panel p = new Panel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		
		kpEntry = new JTextField();
		p.add(createTextPanel(kpEntry, "Kp:"));
		
		kiEntry = new JTextField();
		p.add(createTextPanel(kiEntry, "Ki:"));
		
		kdEntry = new JTextField();
		p.add(createTextPanel(kdEntry, "Kd:"));
		
		speedmultEntry = new JTextField();
		p.add(createTextPanel(speedmultEntry, "SM:"));

		targetpowerEntry = new JTextField();
		p.add(createTextPanel(targetpowerEntry, "TP:"));
		
		errorEntry = new JTextField();
		p.add(createTextPanel(errorEntry, "Error:"));
		
		commitButton = new JButton("Commit");
		commitButton.addMouseListener(bh);
		p.add(commitButton);

		add(p);
	}
	
	private Panel createTextPanel(JTextField text, String label) {
		Panel panel = new Panel(new BorderLayout());
		JLabel l = new JLabel(label);
		Dimension d = l.getPreferredSize();  
	    l.setPreferredSize(new Dimension(40,d.height)); 
		
		panel.add(l, BorderLayout.WEST);
		panel.add(text, BorderLayout.CENTER);
		
		return panel;
	}

	public static void main(String[] args) {
		NXTrc = new JockeyRace();
		NXTrc.setVisible(true);
		NXTrc.requestFocusInWindow();
		NXTrc.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		resetTachos();

		MotorManager.setMotors(leftMotor, rightMotor);
		position = new DeadReckoningManager(leftMotor, rightMotor);
		sensors = new SensorManager();
		
		updateDisplayValues();
		
		foundLine = sensors.canSeeEdge();
		
		while (mode != Mode.Stop) {
			switch (mode) {
			
			case Map:
				mapTrack();
				break;
				
			case Race:
				race();
				break;
			
			default:
				MotorManager.stahp();
				break;
			}
			
			updatePosition();
		}

		MotorManager.stahp();
		sensors.disable();

		System.exit(0);
	}
	
	private static void mapTrack() {
		race();
	}
	
	// This was part of our initial attempt, but it was far too error prone.
	// private static void race() {
		
	// 	if (canSeeObstacle()) {
	// 		// If we find an obstacle, dodge it
	// 		dodgeObstacle();
			
	// 		foundLine = false;
	// 	}
	// 	else {
	// 		if (foundLine) {
	// 			followTrack();
	// 		}
	// 		else {
	// 			int leftPower = 30;
	// 			int rightPower = 30;
				
	// 			if (useInsideTrack) {
	// 				rightPower *= 5/8;
	// 			}
	// 			else {
	// 				leftPower *= 5/8;
	// 			}
				
	// 			MotorManager.forward(leftPower, rightPower);
				
	// 			if (sensors.canSeeEdge()) {
	// 				foundLine = true;
	// 			}
	// 		}
	// 	}
	// }
	
	private static boolean canSeeObstacle() {
		// We should only react to obstacles if we can see them for more than one frame
		if (sensors.canSeeObstacleInFront()) {
			if (sawObstacle) {
				return true;
			}
			else {
				sawObstacle = true;
			}
		}
		else {
			sawObstacle = false;
		}
		
		return false;
	}
	
	// An early attempt to dodge obstacles. It was fairly ballistic.
	private static void dodgeObstacle() {
		turnRightNDegrees(90, 20);
		if (!passObstacleOnSide()) {
			dodgeObstacle();
		}
		
		if (mode == Mode.Wait || mode == Mode.Stop) {
			return;
		}

		turnLeftNDegrees(90, 20);
	}
	
	// Attempts to drive until we can no longer see an obstacle with our side sensor.
	private static boolean passObstacleOnSide() {
		boolean sawObstacle = false;
		boolean canSeeObstacle = sensors.canSeeObstacleToSide();
		
		while (!sawObstacle || canSeeObstacle) {
			if (mode == Mode.Wait || mode == Mode.Stop) {
				return true;
			}
			
			if (!sawObstacle) {
				sawObstacle = canSeeObstacle;
			}
			
			MotorManager.forward(targetpower, targetpower);
			Delay.msDelay(10);
			
			canSeeObstacle = sensors.canSeeObstacleToSide();
		}
		
		// Make sure we're well past
		MotorManager.forward(targetpower, targetpower);
		Delay.msDelay(50);
		
		sawObstacle = false;
		return true;
	}
	
	// Uses a PID controller to follow the edge of the track
	private static int followTrack() {
		int turn = getPIDCorrection(kp, ki, kd, sensors.getLightError(useInsideTrack));
		int power = (int)(targetpower * currentmult);
		driveProportionally(power, useInsideTrack ? -turn : turn);
		
		return turn;
	}
	
	private static int getPIDCorrection(int p, int i, int d, int error) {
		int derivative = error - oldError;

		// We attenuate the integral to force it to converge at a reasonable value
		integral = (int)(integral*0.75 + error);
		sqrIntegral = (int)(sqrIntegral*0.5 + error*error);
		
		int turn = p*error + (int)(i*integral) + d*derivative;
		turn /= 100;
		
		oldError = error;
		
		if (speedmult > 1) {
			if (sqrIntegral < allowedError) {
				currentmult = Math.min(speedmult, currentmult + speedmult/6.0);
			}
			else {
				currentmult = Math.max(1, currentmult*3/4);
			}
		}
		
		return turn;
	}
	
	private static int clamp(int power) {
		//power = Math.max(0, power);
		power = Math.min(100, power);
		
		return power;
	}
	
	private static void driveProportionally(int power, int turn) {
		int bonus = 0;
		if (useInsideTrack) {
			if (turn < -15) {
				bonus = 3*turn;
				Delay.msDelay(50);
			}
			else if (turn > -5){
				power *= 2.5;
			}
		}
		
		MotorManager.forward(clamp(power + turn + bonus), clamp(power - turn));
	}
	
	private static void race() {
		
		if(sensors.canSeeObstacleMiddle() || (!ignoreRightSensor && sensors.canSeeObstacleRight())){
			int angle = 90;
			
			MotorManager.stahp();

			if (sensors.canSeeObstacleMiddle() && foundLine) {
				ignoreRightSensor = true;
				angle = 60;
			}
			
			int power = 10;
			
			MotorManager.backward(power, power);
			Delay.msDelay(1000);
			
			turnRightNDegrees(angle, power);
				
			// If we started on the line, this should prevent us 
			// from getting stuck on the wrong side
			if (foundLine) {
				MotorManager.forward(power, power);
				Delay.msDelay(1000);
			}
			
			resetObstacleVars();
		}
		else if (!foundLine) {
			if (sensors.canSeeObstacleToSide()) {
				MotorManager.forward(10, 10);
			}
			else {
				// Arcs forward and to the left, trying to find the line
				int power = getMidPower()*1/2;
				MotorManager.forward((int)(power*1/2), power);
			}
			
			if (sensors.canSeeOutsideTrack()) {
				turnRightNDegrees(10, 10);
				foundLine = true;
				ignoreRightSensor = false;
			}
		}
		else if (skipBadPart) {
			increasingArc();

			if (!sensors.canSeeWhite()) {
				skipBadPart = false;
				foundLine = sensors.canSeeOutsideTrack();
			}
		}
		else {
			if (integral < -26) {
				// This magic number means that we've found the 270 degree turn, so we can race for the finish
				skipBadPart = true;
				arc = 0.75;
				
				MotorManager.turnLeft(40);
				Delay.msDelay(350);
			}
			else {
				followTrack();
			}
		}
	}
	
	// arc is the percent of power to the right wheel compared to the left one
	private static double arc;
	private static void increasingArc() {
		int power = 80;
		MotorManager.forward(power, (int)(power*arc));
		Delay.msDelay(40);
		
		// Reduce arc, forcing Jockey to turn more sharply
		arc *= 0.98;
	}
	
	private static void resetValues() {
		foundLine = false;
		resetPIDValues();
		resetObstacleVars();
	}
	
	private static void resetObstacleVars() {
		foundLine = false;
		skipBadPart = false;
		obstaclesFound = true;
		
		resetPIDValues();
	}
	
	private static void resetPIDValues() {
		integral = 0;
		sqrIntegral = 0;
		oldError = 0;
		currentmult = 1;
	}
	
	private static void resetTachos() {
		leftMotor.resetTachoCount();
		rightMotor.resetTachoCount();
	}
	
	private static void updatePosition() {
		position.updatePosition();
	}
	
	private static void turnRightNDegrees(double n, int power) {
		position.reset();
		
		while (position.getCurrentHeading() < n * Math.PI/180) {
			MotorManager.turnRight(power);
			
			Delay.msDelay(10);
			updatePosition();
		}
		
		MotorManager.stahp();
	}
	
	private static void turnLeftNDegrees(double n, int power) {
		position.reset();
		
		while (position.getCurrentHeading() > -n * Math.PI/180) {
			MotorManager.turnLeft(power);
			
			Delay.msDelay(10);
			updatePosition();
		}
		
		MotorManager.stahp();
	}
	
	// Part of an abortive simple attempt at mapping. Abandoned because the power issues we were
	// having before the demo meant that we couldn't trust it to work when we needed it to, but
	// our normal values worked well.
	private static void setupValues() {
		if (mappingDone && !obstaclesFound) {
			// There were no obstacles, so we can go a little faster
			speedmult = 14;
		}
	}
	
	// Update our displayed tuning values to the actual ones
	private static void updateDisplayValues() {
		kpEntry.setText(Integer.toString(kp));
		kiEntry.setText(Integer.toString(ki));
		kdEntry.setText(Integer.toString(kd));
		speedmultEntry.setText(Integer.toString(speedmult));
		targetpowerEntry.setText(Integer.toString(targetpower));
		errorEntry.setText(Integer.toString(allowedError));
	}
	
	// Gets new values for our PID variables from the text boxes
	private static void commitValues() {
		kp = new Integer(kpEntry.getText());
		ki = new Integer(kiEntry.getText());
		kd = new Integer(kdEntry.getText());
		speedmult = new Integer(speedmultEntry.getText());
		targetpower = new Integer(targetpowerEntry.getText());
		allowedError = new Integer(errorEntry.getText());
	}
	
	private static int getMaxPower() {
		return targetpower * speedmult;
	}
	
	private static int getMidPower() {
		return getMaxPower() / 2;
	}

	private static class ButtonHandler implements MouseListener, KeyListener {

		public void mouseClicked(MouseEvent arg0) {}

		public void mouseEntered(MouseEvent arg0) {}

		public void mouseExited(MouseEvent arg0) {}

		public void mousePressed(MouseEvent moe) {
			if (moe.getComponent().equals(commitButton)) {
				commitValues();
			}
			else {
				NXTrc.requestFocusInWindow();
			}
		}

		public void mouseReleased(MouseEvent moe) {
			// If you click on the window it should remove focus from the text
			// fields (allowing us to use keyboard commands again)
			NXTrc.requestFocusInWindow();
		}

		// ***********************************************************************
		// Keyboard action
		public void keyPressed(KeyEvent ke) {
			char key = ke.getKeyChar();

			switch (key) {
			
			case 'm':
				mode = Mode.Map;
				position.reset();
				mappingDone = true;
				break;
			
			case 'r':
				mode = Mode.Race;
				position.reset();
				setupValues();
				break;
				
			case 'c':
				mappingDone = false;
				break;

			case 's':
				mode = Mode.Wait;
				break;

			case 'q':
				mode = Mode.Stop;
				break;
			}
			
			resetValues();
		}

		public void keyTyped(KeyEvent ke) {}

		public void keyReleased(KeyEvent ke) {}
	}
}
