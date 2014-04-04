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


public class JockeyControl extends JFrame {

	private static final long serialVersionUID = 1L;

	private enum Mode {
		Stop, Wait, BangBang, P, PI, PD, PID, Obstacles, Project
	}
	
	private enum Track {
		One, Two, Three, Four
	}

	private static Mode mode = Mode.Wait;
	private static Track track = Track.One;

	static MotorPort leftMotor = MotorPort.C;
	static MotorPort rightMotor = MotorPort.A;

	static LightSensor lightSensor = new LightSensor(SensorPort.S1);
	static OpticalDistanceSensor rightIR = new OpticalDistanceSensor(SensorPort.S3);
	static OpticalDistanceSensor leftIR = new OpticalDistanceSensor(SensorPort.S4);
	static OpticalDistanceSensor middleIR = new OpticalDistanceSensor(SensorPort.S2);

	public static JockeyControl NXTrc;

	public static JLabel modeLbl;
	public static JLabel commands;
	public static ButtonHandler bh = new ButtonHandler();
	
	private static JTextField kpEntry, kiEntry, kdEntry, speedmultEntry, targetpowerEntry, backpowerEntry;
	private static JButton commitButton;
	
	private static int kp = 300;
	private static int ki = 0;
	private static int kd = 0;
	
	private static int speedmult = 1;
	private static double currentmult = 1.0;
	
	//private static int offset = 31;
	// Light values
	private static int innerTrack = 32;
	private static int outerTrack = 23;
	private static int whiteTrack = 49;
	private static int specialZone = 43;
	
	
	private static int targetpower = 10;
	private static int backpower = 10;
	
	private static int integral = 0;
	private static int oldError = 0;

	public JockeyControl() {
		setLayout(new FlowLayout());

		setTitle("Jockey Self-Control");
		setBounds(400, 350, 300, 200);
		addMouseListener(bh);
		addKeyListener(bh);

		String cmds = "<html>Buttons:<br>" 
				+ "b: Bang Bang" + "<br>"
				+ "p: P" + "<br>"
				+ "i: PI" + "<br>"
				+ "d: PD" + "<br>"
				+ "a: PID" + "<br>"
				+ "o: Run obstacle course" + "<br>"
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
		
		backpowerEntry = new JTextField();
		p.add(createTextPanel(backpowerEntry, "BP:"));

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
		NXTrc = new JockeyControl();
		NXTrc.setVisible(true);
		NXTrc.requestFocusInWindow();
		NXTrc.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		setKValues(track);
		
		while (mode != Mode.Stop) {
			
			System.out.println(lightSensor.getLightValue());
			
			switch (mode) {
			case BangBang:
				doBangBang();
				break;
				
			case P:
				doP();
				break;
			case PI:
				doPI();
				break;
			case PD:
				doPD();
				break;
				
			case PID:
				doPID();
				break;
				
			case Obstacles:
				runObstacleCourse();
				break;
			
			default:
				stahp();
				break;
			}
		}

		stahp();
		lightSensor.setFloodlight(false);

		System.exit(0);
	}
	
	private static void doBangBang() {
		if (onTrackEdge()) {
			driveForwardAndLeft(targetpower, backpower);
		}
		else {
			driveForwardAndRight(targetpower, backpower);
		}
	}
	
	private static int doP() {
		int turn = getPIDCorrection(kp, 0, 0);
		int power = (int)(targetpower * currentmult);
		driveProportionally(power, turn);
		
		return turn;
	}
	
	private static int doPI() {
		int turn = getPIDCorrection(kp, ki, 0);
		int power = (int)(targetpower * currentmult);
		driveProportionally(power, turn);
		
		return turn;
	}
	
	private static int doPD() {
		int turn = getPIDCorrection(kp, 0, kd);
		int power = (int)(targetpower * currentmult);
		driveProportionally(power, turn);
		
		return turn;
	}
	
	private static int doPID() {
		int turn = getPIDCorrection(kp, ki, kd);
		int power = (int)(targetpower * currentmult);
		driveProportionally(power, turn);
		
		return turn;
	}
	
	private static int getPIDCorrection(int p, int i, int d) {
		int offset = (innerTrack + whiteTrack) / 2;
		
		int lightval = lightSensor.getLightValue();
		int error = lightval - offset;
		
		if (mode == Mode.Project) {
			// The dirtiest of hacks
			error = -error;
		}
		
		int derivative = error - oldError;

		// We attenuate the integral to force it to converge at a reasonable value
		integral = (int)(integral*0.75 + error);
		
		int turn = p*error + i*integral + d*derivative;
		turn /= 100;
		
		oldError = error;
		
		if (speedmult > 1) {
			if (Math.abs(turn) < 3 && Math.abs(derivative) < 3) {
				currentmult = Math.min(speedmult, currentmult + 0.25);
			}
			else
			{
				currentmult = 1.0;
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
		leftMotor.controlMotor(clamp(power + turn), BasicMotorPort.FORWARD);
		rightMotor.controlMotor(clamp(power - turn), BasicMotorPort.FORWARD);
	}
	
	private static void runObstacleCourse() {
		if (rightIR.getDistance() < 175) {
			// Turn left to go around
			int power = targetpower * speedmult;
			backward(power, 0);
			Delay.msDelay(10);
		}
		else if (leftIR.getDistance() < 175) {
			// Back up in the hope that when we go forward in 
			// an arc we pass the obstacle on the right		
			int power = targetpower * speedmult;
			backward(power, power / 4);
			Delay.msDelay(40);
		}
		else if (middleIR.getDistance() < 175) {
			// Back up in the hope that when we go forward in 
			// an arc we pass the obstacle on the right, but the
			// arc is more gentle than other ones
			int power = targetpower * speedmult;
			backward(power, power / 3);
			Delay.msDelay(40);
		}
		else if (lightSensor.getLightValue() > 27){
			// We're on the line, so we should vacate it.
			turnLeft(targetpower * speedmult);
			Delay.msDelay(10);
		}
		else {
			// Arcs forward and to the right, trying to find the line
			int power = targetpower * speedmult;
			forward(power, power * 5 / 8);
		}
	}
	
	private static void resetValues() {
		integral = 0;
		oldError = 0;
		currentmult = 1;
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
	
	private static void turnLeft(int power) {
		leftMotor.controlMotor(power, BasicMotorPort.BACKWARD);
		rightMotor.controlMotor(power, BasicMotorPort.FORWARD);
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
	
	private static void setKValues(Track t) {
		switch (t) {
		case One:
			kp = 300;
			ki = 0;
			kd = 0;
			speedmult = 1;
			targetpower = 30;
			break;
		case Two:
			kp = 600;
			ki = 100;
			kd = 200;
			speedmult = 2;
			targetpower = 30;
			break;
		case Three:
			kp = 600;
			ki = 20;
			kd = 100;
			speedmult = 2;
			targetpower = 10;
			break;
		case Four:
			kp = 200;
			ki = 60;
			kd = 40;
			speedmult = 2;
			targetpower = 10;
			break;
		}
		
		updateDisplayValues();
	}
	
	// Update our displayed tuning values to the actual ones
	private static void updateDisplayValues() {
		kpEntry.setText(Integer.toString(kp));
		kiEntry.setText(Integer.toString(ki));
		kdEntry.setText(Integer.toString(kd));
		speedmultEntry.setText(Integer.toString(speedmult));
		targetpowerEntry.setText(Integer.toString(targetpower));
		backpowerEntry.setText(Integer.toString(backpower));
	}
	
	private static void commitValues() {
		kp = new Integer(kpEntry.getText());
		ki = new Integer(kiEntry.getText());
		kd = new Integer(kdEntry.getText());
		speedmult = new Integer(speedmultEntry.getText());
		targetpower = new Integer(targetpowerEntry.getText());
		backpower = new Integer(backpowerEntry.getText());
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
			case 'b':
				mode = Mode.BangBang;
				break;
			case 'p':
				mode = Mode.P;
				break;
			case 'i':
				mode = Mode.PI;
				break;
			case 'd':
				mode = Mode.PD;
				break;
			case 'a':
				mode = Mode.PID;
				break;
				
			case 't':
				mode = Mode.Project;
				break;
				
			case 'o':
				mode = Mode.Obstacles;
				break;
				
			case '1':
				track = Track.One;
				setKValues(track);
				break;
			case '2':
				track = Track.Two;
				setKValues(track);
				break;
			case '3':
				track = Track.Three;
				setKValues(track);
				break;
			case '4':
				track = Track.Four;
				setKValues(track);
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
