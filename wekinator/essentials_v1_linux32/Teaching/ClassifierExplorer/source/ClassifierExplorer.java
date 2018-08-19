import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import controlP5.*; 
import oscP5.*; 
import netP5.*; 
import java.util.List; 
import java.util.Collections; 
import java.util.Iterator; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class ClassifierExplorer extends PApplet {

// Created by Rebecca Fiebrink 2015








OscP5 oscP5;
NetAddress dest;
WekinatorProxy wp;
String errMsg = "";

//Recording training examples, or generating test examples?
boolean isRecording = true;
int currentClass = 1; //Class of any new training examples

//For synchronization:
Integer locking = new Integer(4);

//For drawing decision boundaries:
boolean isWaitingSingle = false;
boolean readyForNextDecisionLine = false;
int lastSingleX = 0;
int lastSingleY = 0;
int spacing = 5;
int lastYLine = 0;
boolean isDrawingBoundaries = false;


//fast/accurate drawing
boolean isFast = true;

PFont f, f2;
ControlP5 cp5;

int wekinatorReceivePort = 6448;
int processingReceivePort = 12000;
   
int areaTopX = 140;
int areaTopY = 50;
int areaWidth = 450;
int areaHeight = 340;

List<Example> trainingExamples = Collections.synchronizedList(new ArrayList<Example>());
List<Example> testExamples = Collections.synchronizedList(new ArrayList<Example>());

public void setup() {
  size(600, 400, P3D);
  background(0);
  ellipseMode(CENTER);
  
  //Create the font
  f = createFont("Courier", 16);
  textFont(f);
  f2 = createFont("Courier", 12);
  textAlign(LEFT, TOP);

  //Set up OSC for communication to/from Wekinator
  oscP5 = new OscP5(this,processingReceivePort);
  dest = new NetAddress("127.0.0.1",wekinatorReceivePort);
  wp = new WekinatorProxy(oscP5);
  
  createControls();
  updateButtonVisibility(true);
  frameRate(120);
}

public void draw() {
   background(0); 
   smooth();
   drawText();

   if (! isRecording){
     drawTestExamples();
   }
   drawTrainingExamples();
   drawClassifierArea();


   //Drawing decision boundaries?
   //Draw one line of test points at a time to minimize accumulation of errors
   if (readyForNextDecisionLine && !isWaitingSingle) {
      drawDecision();
    } else if (isDrawingBoundaries && !isFast) {
       getNextBoundaryTester(); 
    }
}

public void createControls() {
  cp5 = new ControlP5(this);
  
  cp5.addToggle("isRecording")
     .setPosition(10,20)
     .setSize(75,20)
     .setValue(true)
     .setCaptionLabel("record/run")
     .setMode(ControlP5.SWITCH)
     ;
  
   cp5.addButton("buttonClearTrain")
     .setValue(0)
     .setCaptionLabel("Clear training examples")
     .setPosition(10,90)
     .setSize(120,19)
     ;
     
    cp5.addButton("buttonTrain")
     .setValue(0)
     .setCaptionLabel("Train on these examples")
     .setPosition(10,60)
     .setSize(120,19)
     ;
     
   cp5.addButton("buttonClearTest")
     .setValue(0)
     .setCaptionLabel("Clear test examples")
     .setPosition(10,150)
     .setSize(120,19)
     ;
     
   cp5.addButton("drawDecision")
     .setBroadcast(false)
     .setValue(0)
     .setCaptionLabel("Draw decision boundaries")
     .setPosition(10,120)
     .setSize(120,19)
     .setBroadcast(true)
     ;
    
}

public void drawText() {
  fill(255);

  textFont(f2);

  if (isRecording) {
    text("Recording w/ class=" + currentClass, 140, 15);
    text("(Press number key to change)", 140, 28);
  } else {
    text("Testing", 140, 20);    
  }
  
  textFont(f2);
  text("Run with 2 inputs,", 430, 15);
  text("1 classifier output", 430, 28);
  
  
  text("Current position:", 10, height-100);
  textFont(f);
  text("(" + mouseX + "," + mouseY + ")", 10, height-80);
  text(errMsg, 20, height-30);
  
}

public void drawClassifierArea() {
  stroke(255);
  noFill();
  rect(areaTopX, areaTopY, areaWidth, areaHeight, 7);
}

public void mouseClicked() {
  if (isDrawingBoundaries || !inBounds(mouseX, mouseY)) {
    return;
  }
  if (isRecording) {
    createTrainingExample(mouseX, mouseY, currentClass);
  } else {
    wp.startRunning();
    sendSingleExample(mouseX, mouseY);
  }
}

public void keyPressed() {
  int keyIndex = -1;
  if (key >= '1' && key <= '9') {
    currentClass = key - '1' + 1;
  } else if (key == 'x' || key == 'X') {
    isWaitingSingle = false;
    errMsg = "";
  }
}

public boolean inBounds(int x, int y) {
 if (x < areaTopX || y < areaTopY) {
    return false;
 }
 if (x > areaTopX + areaWidth || y > areaTopY + areaHeight) {
    return false;
 } 
 return true;
}

public void drawTestExamples() {
   synchronized(testExamples) {
    Iterator<Example> iterator = testExamples.iterator();
      while (iterator.hasNext()) {
        iterator.next().draw();
      }
   }
}


public void drawTrainingExamples() {
  synchronized(trainingExamples) {
    Iterator<Example> iterator = trainingExamples.iterator();
      while (iterator.hasNext()) {
        iterator.next().draw();
      }
   }
}


public void buttonClearTest() {
   testExamples.clear();
   lastYLine = 0;
}

public void buttonClearTrain() {
   wp.deleteTraining();
   trainingExamples.clear();
   lastYLine = 0;
}

public void sendSingleExample(int x, int y) {
  synchronized(locking) {
    if (! isWaitingSingle) {
      isWaitingSingle = true;
      lastSingleX = x;
      lastSingleY = y;
      OscMessage msg = new OscMessage("/wek/inputs");
      msg.add((float)x); 
      msg.add((float)y);
      oscP5.send(msg, dest);  
    } else {
      println("Error; Tried to send new message but haven't received last response yet");
      errMsg = "Communication error with Wekinator; type X to reset";
    }
  }
}

public void createTrainingExample(int x, int y, int c) {
   wp.startRecording();
   wp.setClass(c);
   trainingExamples.add(new Example(x, y, c, false));
   wp.sendInputs(x, y);
}


public void addTestExample(int x, int y, int c) {
  testExamples.add(new Example(x, y, c, true)); //for display
}
  
public void oscEvent(OscMessage theOscMessage) {
 synchronized(locking) {
  int c = getClassValue(theOscMessage); 

  if (isWaitingSingle) {
         addTestExample(lastSingleX, lastSingleY, c);
         isWaitingSingle = false;
         //println("Received: " + lastSingleX + "," + lastSingleY + ": " + c);
         if (isDrawingBoundaries && isFast) {
           getNextBoundaryTester();
         }
    } else {
      //This does get called, due to some duplicate OSC messages
      
      println("RECEIVED BUT NOT WAITING: " + c);
    }  
 } 
}

public int getClassValue(OscMessage theOscMessage) {
      return wp.getClassValue(theOscMessage);
} 

public void drawDecision() {
      isWaitingSingle = false;
      errMsg = "";
  
    //println("DRAWING DECISION");
    readyForNextDecisionLine = false;
    lastYLine += spacing;
    isDrawingBoundaries = true;
    wp.startRunning();
    sendSingleExample(areaTopX, areaTopY+lastYLine);
}

public void getNextBoundaryTester() {
   int nextX = lastSingleX + spacing;
   if (nextX > areaTopX + areaWidth) {
     if (lastSingleY >= areaTopY + areaHeight) {
       isDrawingBoundaries = false;
     } else {
          readyForNextDecisionLine = true;
     }
   } else {
     sendSingleExample(nextX, lastSingleY);
   }
}

public void buttonTrain() {
    isWaitingSingle = false;
      errMsg = "";
   wp.train(); 
}

public void updateButtonVisibility(boolean rec) {
  if (rec) {
    try {
      cp5.getController("drawDecision").setVisible(false);
      cp5.getController("buttonClearTest").setVisible(false);
      cp5.getController("buttonClearTrain").setVisible(true);
     // cp5.getController("buttonTrain").setVisible(true);
    } catch (Exception ex) {
    }
  } else {
    try {
      cp5.getController("drawDecision").setVisible(true);
      cp5.getController("buttonClearTest").setVisible(true);
      cp5.getController("buttonClearTrain").setVisible(false);
     // cp5.getController("buttonTrain").setVisible(false);
    } catch (Exception ex) {
    }
  }
}

public void isRecording(boolean rValue) {
  isWaitingSingle = false;
  errMsg = "";
  isRecording = rValue;
  if (rValue) {
     isDrawingBoundaries = false; 
  }
  updateButtonVisibility(rValue);
}
// Created by Rebecca Fiebrink 2015


public class Example {
   int x, y;
   int r, g, b, a;
   boolean isTesting;
   
   public Example(int x, int y, int c, boolean isTesting) {
      this.x = x;
      this.y = y;
      this.isTesting = isTesting;
      
      a = (isTesting ? 130 : 255);
      
     //Set color according to class
     if (c == 1) {
       r = 255;
       g = 0;
       b = 0;
     } else if (c == 2) {
       r = 0;
       g = 164;
       b = 255;
     } else if (c == 3) {
       r = 0;
       g = 232;
       b = 124;
     } else if (c ==4) {
       r = 232;
       g = 224;
       b = 0;
     } else if (c ==5) {
       r= 232;
       g = 0;
       b= 200;
     } else if (c ==6) {
       r= 37;
       g= 3;
       b=  232;
     } else if (c ==7) {
       r = 255;
       g = 148;
       b = 3;
     } else if (c ==8) {
       r = 255;
       g = 255;
       b = 255;
     } else {
       r = 235;
       g = 255;
       b = 136;
     } 
   }
   
   public void draw() {
       noStroke();
       fill(r,g,b,a);
       if (isTesting) {
         ellipse(x, y, 5, 5);
       } else {
         ellipse(x, y, 10, 10);

       }
   }
}
// Created by Rebecca Fiebrink 2015

public class WekinatorProxy {
   OscP5 oscP5;
   
   public WekinatorProxy(OscP5 oscP5) {
       this.oscP5 = oscP5;
   }

   public void sendInputs(int x, int y) {
       //println("Sending msg " + x + ","  + y);
       OscMessage msg = new OscMessage("/wek/inputs");
       msg.add((float)x); 
       msg.add((float)y);
       oscP5.send(msg, dest);
   }
  
   public void setClass(int c) {
       OscMessage msg = new OscMessage("/wekinator/control/outputs");
      // println("setting class to " + c);
       msg.add((float)c); 
       oscP5.send(msg, dest);
   }
   
   public void startRecording() {
       OscMessage msg = new OscMessage("/wekinator/control/startRecording");
       //println("Recording");
       oscP5.send(msg, dest);
   }
   
   public void stopRecording() {
       OscMessage msg = new OscMessage("/wekinator/control/stopRecording");
      // println("Stoping recording");
       oscP5.send(msg, dest);
   }
   
   public void train() {
       OscMessage msg = new OscMessage("/wekinator/control/train");
       //println("Recording");
       oscP5.send(msg, dest);
   }
   
   public void startRunning() {
       OscMessage msg = new OscMessage("/wekinator/control/startRunning");
      // println("Running");
       oscP5.send(msg, dest);
     
   }
   
   public void stopRunning() {
       OscMessage msg = new OscMessage("/wekinator/control/stopRecording");
       //println("Stop running");
       oscP5.send(msg, dest);     
   }
   
   public void deleteTraining() {
       OscMessage msg = new OscMessage("/wekinator/control/deleteAllExamples");
      // println("Deleting examples");
       oscP5.send(msg, dest);
   }
   
   public int getClassValue(OscMessage theOscMessage) {
      if (theOscMessage.checkAddrPattern("/wek/outputs")) {
        if(theOscMessage.checkTypetag("f")) {
          return (int) theOscMessage.get(0).floatValue();
        }
      }
      println("Error parsing class OSC message");
      return -1;
   }
}
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "ClassifierExplorer" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
