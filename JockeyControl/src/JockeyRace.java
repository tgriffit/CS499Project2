import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import lejos.nxt.*;
import lejos.util.Delay;


public class JockeyRace extends JFrame {

	private static final long serialVersionUID = 1L;

	private enum Mode {
		Stop, Wait, Map, Race
	}
	
	private enum DodgeMode {
		Left, Right
	}

	private static Mode mode = Mode.Wait;

	static MotorPort leftMotor = MotorPort.C;
	static MotorPort rightMotor = MotorPort.A;

	static LightSensor lightSensor = new LightSensor(SensorPort.S1);
	static OpticalDistanceSensor rightIR = new OpticalDistanceSensor(SensorPort.S3);
	static OpticalDistanceSensor leftIR = new OpticalDistanceSensor(SensorPort.S4);
	static OpticalDistanceSensor sideIR = new OpticalDistanceSensor(SensorPort.S2);

	public static JockeyRace NXTrc;

	public static JLabel modeLbl;
	public static JLabel commands;
	public static ButtonHandler bh = new ButtonHandler();
	
	private static JTextField kpEntry, kiEntry, kdEntry, speedmultEntry, targetpowerEntry;
	private static JButton commitButton;
	
	private static int kp = 300;
	private static int ki = 20;
	private static int kd = 200;
	
	private static int targetpower = 10;
	
	private static int speedmult = 3;
	private static double currentmult = 1.0;
	
	// Light values
	private static int innerTrack = 30;
	private static int outerTrack = 22;
	private static int whiteTrack = 47;
	private static int specialZone = 41;
	
	private static DeadReckoningManager position;
	
	private static int integral = 0;
	private static int sqrIntegral = 0;
	private static int oldError = 0;
	
	private static boolean foundLine = false;

	public JockeyRace() {
		setLayout(new FlowLayout());

		setTitle("Jockey Self-Control");
		setBounds(400, 350, 300, 200);
		addMouseListener(bh);
		addKeyListener(bh);

		String cmds = "<html>Buttons:<br>" 
				+ "m: Map Track<br>"
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
		position = new DeadReckoningManager(leftMotor.getTachoCount(), rightMotor.getTachoCount());
		updateDisplayValues();
		
		while (mode != Mode.Stop) {
			//System.out.println(lightSensor.getLightValue());
			//System.out.println("l: " + leftIR.getDistance() + " r: " + rightIR.getDistance() + " s: " + sideIR.getDistance());
			
			switch (mode) {
			
			case Map:
				mapTrack();
				break;
				
			case Race:
				race();
				break;
			
			default:
				stahp();
				break;
			}
			
			//Delay.msDelay(10);
			updatePosition();
		}

		stahp();
		lightSensor.setFloodlight(false);

		System.exit(0);
	}
	
	private static void useCamera() {
		// NOPE!
	}
	
	private static void mapTrack() {
		
		// Bang-Bang our way around the track in style
		if (onTrackEdge()) {
			forward(targetpower, 0);
		}
		else {
			forward(0, targetpower);
		}
	}
	
	private static void race() {
		if (canSeeObstacle(leftIR) || canSeeObstacle(rightIR)) {
			// If we find an obstacle, dodge it
			dodgeObstacle();
			
			foundLine = false;
			// TODO: React to obstacles found during this movement
			
			// Arc to get back to the line
//			while (!onTrackEdge()) {
//				forward(targetpower * 5 / 8, targetpower);
//				Delay.msDelay(10);
//			}
		}
		else {
			if (foundLine) {
				followTrack();
			}
			else {
				forward(targetpower * 5 / 8, targetpower);
				
				if (onTrackEdge()) {
					foundLine = true;
				}
			}
		}
	}
	
	private static void dodgeObstacle() {
		turn90Left();
		if (!passObstacleOnSide()) {
			dodgeObstacle();
		}
		
		if (mode == Mode.Wait || mode == Mode.Stop) {
			return;
		}
		
		turn90Right();
		//passObstacleOnSide();
	}
	
	private static boolean passObstacleOnSide() {
		boolean sawObstacle = false;
		boolean canSeeObstacle = canSeeObstacle(sideIR);
		
		while (!sawObstacle || canSeeObstacle) {
			if (mode == Mode.Wait || mode == Mode.Stop) {
				return true;
			}
			
			if (!sawObstacle) {
				sawObstacle = canSeeObstacle;
			}
			
			forward(targetpower, targetpower);
			Delay.msDelay(10);
			
			canSeeObstacle = canSeeObstacle(sideIR);
		}
		
		// Make sure we're well past
		forward(targetpower, targetpower);
		Delay.msDelay(50);
		
		sawObstacle = false;
		return true;
	}
	
	private static int followTrack() {
		int midval = outerTrack/3 + whiteTrack*2/3;
		
		int turn = getPIDCorrection(kp, ki, kd, midval, lightSensor.getLightValue());
		int power = (int)(targetpower * currentmult);
		driveProportionally(power, turn);
		
		return turn;
	}
	
	private static int getPIDCorrection(int p, int i, int d, int offset, int val) {
		int error = val - offset;
		int derivative = error - oldError;

		// We attenuate the integral to force it to converge at a reasonable value
		integral = (int)(integral*0.75 + error);
		sqrIntegral = (int)(sqrIntegral*0.5 + error*error);
		
		int turn = p*error + (int)(i*integral) + d*derivative;
		turn /= 100;
		
		oldError = error;
		
		if (speedmult > 1) {
			if (sqrIntegral < 4) {
				currentmult = Math.min(speedmult, currentmult + 0.25);
			}
			else {
				currentmult = Math.max(1, currentmult*2/3);
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
		leftMotor.controlMotor(clamp(power - turn), BasicMotorPort.FORWARD);
		rightMotor.controlMotor(clamp(power + turn), BasicMotorPort.FORWARD);
	}
	
//	private static void runObstacleCourse() {
//		if (rightIR.getDistance() < 175) {
//			// Turn left to go around
//			int power = targetpower * speedmult;
//			backward(power, 0);
//			Delay.msDelay(10);
//		}
//		else if (leftIR.getDistance() < 175) {
//			// Back up in the hope that when we go forward in 
//			// an arc we pass the obstacle on the right		
//			int power = targetpower * speedmult;
//			backward(power, power / 4);
//			Delay.msDelay(40);
//		}
//		else if (middleIR.getDistance() < 175) {
//			// Back up in the hope that when we go forward in 
//			// an arc we pass the obstacle on the right, but the
//			// arc is more gentle than other ones
//			int power = targetpower * speedmult;
//			backward(power, power / 3);
//			Delay.msDelay(40);
//		}
//		else if (lightSensor.getLightValue() > 27){
//			// We're on the line, so we should vacate it.
//			turnLeft(targetpower * speedmult);
//			Delay.msDelay(10);
//		}
//		else {
//			// Arcs forward and to the right, trying to find the line
//			int power = targetpower * speedmult;
//			forward(power, power * 5 / 8);
//		}
//	}
	
	private static void resetValues() {
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
		position.updatePosition(leftMotor.getTachoCount(), rightMotor.getTachoCount());
		//System.out.println(position.x + " : " + position.y + " : " + position.heading);
	}
	
	// Used by bang bang
	private static void driveForwardAndLeft(int power, int back) {
		leftMotor.controlMotor(back, BasicMotorPort.BACKWARD);
		rightMotor.controlMotor(power, BasicMotorPort.FORWARD);
	}
	
	private static void driveForwardAndRight(int power, int back) {
		leftMotor.controlMotor(power, BasicMotorPort.FORWARD);
		rightMotor.controlMotor(back, BasicMotorPort.BACKWARD);
	}
	
	private static void turn90Right() {
		position.reset();
		
		while (position.heading < DeadReckoningManager.NinetyDegrees) {
			turnRight(30);
			
			Delay.msDelay(10);
			updatePosition();
		}
		
		stahp();
		
//		if (position.heading > -DeadReckoningManager.NinetyDegrees)
//			turnLeft(30);
//		else
//			stahp();
//		break;
//		
//	case Race:
//		if (position.heading < DeadReckoningManager.NinetyDegrees)
//			turnRight(30);
//		else
//			stahp();
//		break;
	}
	
	private static void turn90Left() {
		position.reset();
		
		while (position.heading > -DeadReckoningManager.NinetyDegrees) {
			turnLeft(30);
			
			Delay.msDelay(10);
			updatePosition();
		}
		
		stahp();
	}
	
	private static void turnLeft(int power) {
		leftMotor.controlMotor(power, BasicMotorPort.BACKWARD);
		rightMotor.controlMotor(power, BasicMotorPort.FORWARD);
	}
	
	private static void turnRight(int power) {
		leftMotor.controlMotor(power, BasicMotorPort.FORWARD);
		rightMotor.controlMotor(power, BasicMotorPort.BACKWARD);
	}
	
	private static void backward(int left, int right) {
		leftMotor.controlMotor(left, BasicMotorPort.BACKWARD);
		rightMotor.controlMotor(right, BasicMotorPort.BACKWARD);
	}
	
	private static void forward(int left, int right) {
		leftMotor.controlMotor(left, BasicMotorPort.FORWARD);
		rightMotor.controlMotor(right, BasicMotorPort.FORWARD);
	}
	
	private static boolean onTrackEdge() {
		// Approximate. Anything darker than the midrange of a special zone 
		// and the inner line should be one of the track boundaries
		int offset = (innerTrack + specialZone) / 2;
		return lightSensor.getLightValue() < offset;
	}
	
	private static boolean canSeeObstacle(OpticalDistanceSensor sensor) {
		return sensor.getDistance() < 100;
	}
	
	// Update our displayed tuning values to the actual ones
	private static void updateDisplayValues() {
		kpEntry.setText(Integer.toString(kp));
		kiEntry.setText(Integer.toString(ki));
		kdEntry.setText(Integer.toString(kd));
		speedmultEntry.setText(Integer.toString(speedmult));
		targetpowerEntry.setText(Integer.toString(targetpower));
	}
	
	private static void commitValues() {
		kp = new Integer(kpEntry.getText());
		ki = new Integer(kiEntry.getText());
		kd = new Integer(kdEntry.getText());
		speedmult = new Integer(speedmultEntry.getText());
		targetpower = new Integer(targetpowerEntry.getText());
	}

	// Stahps.
	private static void stahp() {
		leftMotor.controlMotor(100, BasicMotorPort.STOP);
		rightMotor.controlMotor(100, BasicMotorPort.STOP);
	}

	private static class ButtonHandler implements MouseListener, KeyListener {

		public void mouseClicked(MouseEvent arg0) {
		}

		public void mouseEntered(MouseEvent arg0) {
		}

		public void mouseExited(MouseEvent arg0) {
		}

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
//			case 'a':
//				mode = Mode.PID;
//				break;
//				
//			case 'o':
//				mode = Mode.Obstacles;
//				break;
//				
//			case 'r':
//				mode = Mode.Rotate;
//				position.heading = 0;
//				break;
			
			case 'm':
				mode = Mode.Map;
				position.reset();
				break;
			
			case 'r':
				mode = Mode.Race;
				position.reset();
				break;
				
			case '1':
				turn90Left();
				break;
			case '2':
				turn90Right();
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

		public void keyTyped(KeyEvent ke) {
		}

		public void keyReleased(KeyEvent ke) {
		}
	}
}
