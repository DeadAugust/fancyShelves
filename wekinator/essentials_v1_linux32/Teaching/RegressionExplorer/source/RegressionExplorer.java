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

public class RegressionExplorer extends PApplet {

// Created by Rebecca Fiebrink 2015








OscP5 oscP5;
NetAddress dest;
WekinatorProxy wp;

String errMsg = "";

//Recording training examples, or generating test examples?
boolean isRecording = true;

//For drawing modeled curve:
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
int areaHeight = 280;
int vertGap = 10;
int testHeight = 40;

List<Example> trainingExamples = Collections.synchronizedList(new ArrayList<Example>());
List<Example> testExamples = Collections.synchronizedList(new ArrayList<Example>());

public void setup() {
  size(600, 400, P3D);
  background(0);
  
  //Create the font
  f = createFont("Courier", 16);
  textFont(f);
  textAlign(LEFT, TOP);
  f2 = createFont("Courier", 12);

  //Set up OSC for communication to/from Wekinator
  oscP5 = new OscP5(this,processingReceivePort);
  dest = new NetAddress("127.0.0.1",wekinatorReceivePort);
  wp = new WekinatorProxy(oscP5);
  
  createControls();
  updateButtonVisibility(true);
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
  drawTestingArea();

   //Drawing line?
    if (isDrawingBoundaries && !isFast) {
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
     .setCaptionLabel("Draw regression line")
     .setPosition(10,120)
     .setSize(120,19)
     .setBroadcast(true)
     ;
    
}

public void drawText() {
  fill(255);

if (isRecording) {
    text("Recording (Run with 1 input, 1 continuous output)", 90, 20);
  } else {
    text("Testing", 90, 20); 
    textFont(f2);
    text("Click here to make", 10, height - 50);
    text("      test points:", 10, height - 35);

  }
  
  textFont(f2);
  text("Current position:", 10, height-120);
  textFont(f);
  if (inTestBounds(mouseX, mouseY)) {
    text("Input=" + mouseX, 10, height-100);
    text(errMsg, 20, height-30);
  } else {
    text("Input=" + mouseX + "\nOutput=" + mouseY, 10, height-100);
    text(errMsg, 20, height-30);
  }
  
}

public void drawClassifierArea() {
  stroke(255);
  noFill();
  rect(areaTopX, areaTopY, areaWidth, areaHeight, 7);
}

public void drawTestingArea() {
  stroke(255);
  noFill();
  rect(areaTopX, areaTopY + areaHeight + vertGap, areaWidth, testHeight, 7);
}

public void mouseClicked() {
  if (isDrawingBoundaries) {
    return;
  }
  if (isRecording && inBounds(mouseX, mouseY)) {
    createTrainingExample(mouseX, mouseY);
  } else if (!isRecording && inTestBounds(mouseX, mouseY)) {
    wp.startRunning();
    sendSingleExample(mouseX);
  } /*else {
    println("Outside bounds");
  } */
}

public void keyPressed() {
  int keyIndex = -1;
  if (key == 'x' || key == 'X') {
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

public boolean inTestBounds(int x, int y) {
  if (x < areaTopX || y < areaTopY + areaHeight + vertGap) {
    return false;
   }
  if (x > areaTopX + areaWidth || y > areaTopY + areaHeight + vertGap + testHeight) {
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
}

public void buttonClearTrain() {
   wp.deleteTraining();
   trainingExamples.clear();
}

public void sendSingleExample(int x) {
    if (! isWaitingSingle) {
      isWaitingSingle = true;
      lastSingleX = x;
      OscMessage msg = new OscMessage("/wek/inputs");
      msg.add((float)x); 
      oscP5.send(msg, dest);  
    } else {
      println("Error; Tried to send new message but haven't received last response yet");
      errMsg = "Communication error with Wekinator; type X to reset";

    }
}

public void createTrainingExample(int x, int y) {
   wp.startRecording();
   wp.setOutput(y);
   trainingExamples.add(new Example(x, y, false));
   wp.sendInputs(x);
}


public void addTestExample(int x, float y) {
  testExamples.add(new Example(x, y, true)); //for display
}
  
public void oscEvent(OscMessage theOscMessage) {
 // synchronized(locking) {
  float f = getOutputValue(theOscMessage); 

  if (isWaitingSingle) {
         addTestExample(lastSingleX, f);
         isWaitingSingle = false;
         
         if (isDrawingBoundaries && isFast) {
           getNextBoundaryTester();
         }
    } else {
      //This does get called, due to some duplicate OSC messages
      
     // println("RECEIVED BUT NOT WAITING");
    }  
  //} 
}

public float getOutputValue(OscMessage theOscMessage) {
      return wp.getOutputValue(theOscMessage);
} 

public void drawDecision() {
    isWaitingSingle = false;
    errMsg = "";
    isDrawingBoundaries = true;
    wp.startRunning();
    sendSingleExample(areaTopX);
}

public void getNextBoundaryTester() {
   int nextX = lastSingleX + spacing;
   if (nextX > areaTopX + areaWidth) {
       isDrawingBoundaries = false;
   } else {
     sendSingleExample(nextX);
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
      //cp5.getController("buttonTrain").setVisible(false);
    } catch (Exception ex) {
    }
  }
}

public void isRecording(boolean rValue) {
  isWaitingSingle = false;
  errMsg = "";
  isRecording = rValue;
  updateButtonVisibility(rValue);
}
// Created by Rebecca Fiebrink 2015


public class Example {
   int x;
   float y;
   int a;
   int r = 0, g=255, b=0;
   boolean isTesting;
   
   public Example(int x, float y, boolean isTesting) {
      this.x = x;
      this.y = y;
      this.isTesting = isTesting;
      
      a = (isTesting ? 150 : 255);
      
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

   public void sendInputs(int x) {
       //println("Sending msg " + x + ","  + y);
       OscMessage msg = new OscMessage("/wek/inputs");
       msg.add((float)x); 
       oscP5.send(msg, dest);
   }
  
   public void setOutput(float f) {
       OscMessage msg = new OscMessage("/wekinator/control/outputs");
       println("setting output to " + f);
       msg.add(f); 
       oscP5.send(msg, dest);
   }
   
   public void startRecording() {
       OscMessage msg = new OscMessage("/wekinator/control/startRecording");
       println("Recording");
       oscP5.send(msg, dest);
   }
   
   public void stopRecording() {
       OscMessage msg = new OscMessage("/wekinator/control/stopRecording");
       println("Stoping recording");
       oscP5.send(msg, dest);
   }
   
   public void train() {
       OscMessage msg = new OscMessage("/wekinator/control/train");
       println("Recording");
       oscP5.send(msg, dest);
   }
   
   public void startRunning() {
       OscMessage msg = new OscMessage("/wekinator/control/startRunning");
       println("Running");
       oscP5.send(msg, dest);
     
   }
   
   public void stopRunning() {
       OscMessage msg = new OscMessage("/wekinator/control/stopRecording");
       println("Stop running");
       oscP5.send(msg, dest);     
   }
   
   public void deleteTraining() {
       OscMessage msg = new OscMessage("/wekinator/control/deleteAllExamples");
       println("Deleting examples");
       oscP5.send(msg, dest);
   }
   
   public float getOutputValue(OscMessage theOscMessage) {
      if (theOscMessage.checkAddrPattern("/wek/outputs")) {
        if(theOscMessage.checkTypetag("f")) {
          return theOscMessage.get(0).floatValue();
        }
      }
      println("Error parsing OSC message");
      return -1;
   }
}
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "RegressionExplorer" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
