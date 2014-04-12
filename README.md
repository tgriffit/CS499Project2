CMPUT 499 Project 2
=============

The JockeyControl folder contains a single eclipse project. In order to run it, simply import the project into eclipse and run it as a Java Application. The GUI lists the commands that are implemented.

If you want to alter the PID values, simply alter the values in the text boxes and hit the commit button. The values are as follows:
	Kp: The Kp value of the PID controller
	Ki: The Ki value of the PID controller
	Kd: The Kd value of the PID controller
	SM: The maximum multiple of the base power we'll reach when error is low
	TP: The base power to the wheels
	Error: The allowed amount of our squared integral. Any value less than this will result in increasing our speed.